/*
 * Copyright (c) 2026, Ron Young <https://github.com/raiyni>
 * All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package melky.resourcepacks.generator;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackVarsGenerator
{
	private static final String INTERFACE_ID_URL =
		"https://github.com/runelite/runelite/raw/refs/heads/master/runelite-api/src/main/java/net/runelite/api/gameval/InterfaceID.java";
	private static final String OUTPUT_PATH = "src/main/java/melky/resourcepacks/features/packs/PackVars.java";
	private static final String OVERRIDES_TOML_PATH = "src/main/resources/overrides/overrides.toml";
	private static final String TEMPLATE_PATH = "src/generator/resources/PackVars.java.template";

	private static final Pattern SECTION_PATTERN = Pattern.compile("^\\[\\[?([^]]+)\\]?.*$");
	private static final Pattern INTERFACE_NUMERIC_PATTERN = Pattern.compile("^interface=(\\d+)$");
	private static final Pattern INTERFACE_TEMPLATE_PATTERN = Pattern.compile("^interface=\"\\$\\{(\\w+)}\"$");
	private static final Pattern CHILDREN_NUMERIC_PATTERN = Pattern.compile("^children=\\[([\\d,\\s]+)]$");
	private static final Pattern CHILDREN_TEMPLATE_PATTERN = Pattern.compile("\"\\$\\{(\\w+)}\"");

	public static void main(String[] args) throws Exception
	{
		System.out.println("Downloading " + INTERFACE_ID_URL + "...");
		Map<Integer, String> reverseMap = new HashMap<>();
		Map<String, String> nameToRef = new HashMap<>();
		Map<String, String> childRefs = new HashMap<>();
		Map<Long, String> packedToFieldName = new HashMap<>();
		readConstants(INTERFACE_ID_URL, reverseMap, nameToRef, childRefs, packedToFieldName);
		Map<String, Integer> forwardMap = new HashMap<>();
		for (var entry : reverseMap.entrySet())
		{
			forwardMap.put(entry.getValue(), entry.getKey());
		}
		System.out.println("InterfaceID contains " + reverseMap.size() + " constants");
		System.out.println("InterfaceID contains " + childRefs.size() + " child constants");

		migrateOverridesToml(reverseMap, forwardMap, packedToFieldName);

		System.out.println("Reading " + OVERRIDES_TOML_PATH + "...");
		String overridesContent = Files.readString(Paths.get(OVERRIDES_TOML_PATH), StandardCharsets.UTF_8);
		Set<Integer> neededIds = extractInterfaceIdsFromOverrides(overridesContent, forwardMap);
		Set<String> childKeys = extractChildIds(overridesContent);
		System.out.println("Found " + neededIds.size() + " unique interface IDs");
		System.out.println("Found " + childKeys.size() + " unique child IDs");

		List<String> interfaceEntries = new ArrayList<>();
		for (int id : neededIds)
		{
			String name = reverseMap.get(id);
			if (name != null)
			{
				String ref = nameToRef.get(name);
				if (ref != null)
				{
					interfaceEntries.add(String.format("\t\t\t.put(\"%s\", %s)", name, ref));
				}
				else
				{
					System.err.println("WARNING: No reference for InterfaceID constant: " + name);
				}
			}
			else
			{
				System.err.println("WARNING: No InterfaceID constant found for interface=" + id);
			}
		}

		List<String> childrenEntries = new ArrayList<>();
		for (String key : childKeys)
		{
			String ref = childRefs.get(key);
			if (ref != null)
			{
				childrenEntries.add(String.format("\t\t\t.put(\"%s\", %s)", key, ref));
			}
			else
			{
				System.err.println("WARNING: No child ref for key: " + key);
			}
		}

		Collections.sort(interfaceEntries);
		Collections.sort(childrenEntries);

		String source = buildPackVarsSource(interfaceEntries, childrenEntries);
		Path outputPath = Paths.get(OUTPUT_PATH);
		Files.createDirectories(outputPath.getParent());
		Files.writeString(outputPath, source);

		System.out.println("Generated " + OUTPUT_PATH + " with " + (interfaceEntries.size() + childrenEntries.size()) + " entries");
	}

	private static void migrateOverridesToml(
		Map<Integer, String> reverseMap,
		Map<String, Integer> forwardMap,
		Map<Long, String> packedToFieldName) throws Exception
	{
		Path tomlPath = Paths.get(OVERRIDES_TOML_PATH);
		String content = Files.readString(tomlPath, StandardCharsets.UTF_8);

		Map<String, String> sectionToInterface = buildSectionToInterfaceMap(content, reverseMap);

		StringBuilder result = new StringBuilder();
		String currentSection = null;
		int interfaceReplaced = 0;
		int childrenReplaced = 0;

		for (String line : content.split("\n"))
		{
			String trimmed = line.trim();

			Matcher sectionMatcher = SECTION_PATTERN.matcher(trimmed);
			if (sectionMatcher.matches())
			{
				currentSection = sectionMatcher.group(1);
				result.append(line).append("\n");
				continue;
			}

			Matcher interfaceMatcher = INTERFACE_NUMERIC_PATTERN.matcher(trimmed);
			if (interfaceMatcher.matches())
			{
				int id = Integer.parseInt(interfaceMatcher.group(1));
				String name = reverseMap.get(id);
				if (name != null)
				{
					result.append(line.replace(trimmed, "interface=\"${" + name + "}\"")).append("\n");
					interfaceReplaced++;
					continue;
				}
			}

			Matcher childrenMatcher = CHILDREN_NUMERIC_PATTERN.matcher(trimmed);
			if (childrenMatcher.matches() && currentSection != null)
			{
				String interfaceName = resolveInterface(currentSection, sectionToInterface);
				if (interfaceName != null)
				{
					Integer ifId = forwardMap.get(interfaceName);
					String[] parts = childrenMatcher.group(1).split(",");
					StringBuilder newChildren = new StringBuilder("children=[");
					boolean replaced = false;
					for (int i = 0; i < parts.length; i++)
					{
						if (i > 0)
						{
							newChildren.append(", ");
						}
						int childId = Integer.parseInt(parts[i].trim());
						if (ifId != null)
						{
							long packed = ((long) ifId << 16) | childId;
							String fieldName = packedToFieldName.get(packed);
							if (fieldName != null)
							{
								newChildren.append("\"${").append(interfaceName).append("_").append(fieldName).append("}\"");
								replaced = true;
								continue;
							}
						}
						newChildren.append(childId);
					}
					newChildren.append("]");
					result.append(line.replace(trimmed, newChildren.toString())).append("\n");
					if (replaced)
					{
						childrenReplaced++;
					}
					continue;
				}
			}

			result.append(line).append("\n");
		}

		Files.writeString(tomlPath, result.toString());
		System.out.println("Migrated " + OVERRIDES_TOML_PATH + ": replaced " + interfaceReplaced + " interface IDs, " + childrenReplaced + " children IDs");
	}

	private static Set<Integer> extractInterfaceIdsFromOverrides(String content, Map<String, Integer> forwardMap)
	{
		Set<Integer> ids = new TreeSet<>();
		for (String line : content.split("\n"))
		{
			String trimmed = line.trim();
			Matcher numericMatcher = INTERFACE_NUMERIC_PATTERN.matcher(trimmed);
			if (numericMatcher.matches())
			{
				ids.add(Integer.parseInt(numericMatcher.group(1)));
				continue;
			}
			Matcher templateMatcher = INTERFACE_TEMPLATE_PATTERN.matcher(trimmed);
			if (templateMatcher.matches())
			{
				String name = templateMatcher.group(1);
				Integer id = forwardMap.get(name);
				if (id != null)
				{
					ids.add(id);
				}
				else
				{
					System.err.println("WARNING: No InterfaceID constant for name: " + name);
				}
			}
		}
		return ids;
	}

	private static Map<String, String> buildSectionToInterfaceMap(String content, Map<Integer, String> reverseMap)
	{
		Map<String, String> sectionToInterface = new HashMap<>();
		String currentSection = null;
		for (String line : content.split("\n"))
		{
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#"))
			{
				continue;
			}

			Matcher sectionMatcher = SECTION_PATTERN.matcher(trimmed);
			if (sectionMatcher.matches())
			{
				currentSection = sectionMatcher.group(1);
				continue;
			}

			if (currentSection == null)
			{
				continue;
			}

			Matcher templateMatcher = INTERFACE_TEMPLATE_PATTERN.matcher(trimmed);
			if (templateMatcher.matches())
			{
				sectionToInterface.put(currentSection, templateMatcher.group(1));
				continue;
			}

			Matcher numericMatcher = INTERFACE_NUMERIC_PATTERN.matcher(trimmed);
			if (numericMatcher.matches())
			{
				int id = Integer.parseInt(numericMatcher.group(1));
				String name = reverseMap.get(id);
				if (name != null)
				{
					sectionToInterface.put(currentSection, name);
				}
			}
		}
		return sectionToInterface;
	}

	private static String resolveInterface(String section, Map<String, String> sectionToInterface)
	{
		String current = section;
		while (current != null)
		{
			String interfaceName = sectionToInterface.get(current);
			if (interfaceName != null)
			{
				return interfaceName;
			}
			int lastDot = current.lastIndexOf('.');
			if (lastDot < 0)
			{
				break;
			}
			current = current.substring(0, lastDot);
		}
		return null;
	}

	private static Set<String> extractChildIds(String content)
	{
		Set<String> childKeys = new TreeSet<>();
		for (String line : content.split("\n"))
		{
			String trimmed = line.trim();
			if (!trimmed.startsWith("children=["))
			{
				continue;
			}

			Matcher templateMatcher = CHILDREN_TEMPLATE_PATTERN.matcher(trimmed);
			while (templateMatcher.find())
			{
				childKeys.add(templateMatcher.group(1));
			}
		}
		return childKeys;
	}

	private static void readConstants(
		String url,
		Map<Integer, String> reverseMap,
		Map<String, String> nameToRef,
		Map<String, String> childRefs,
		Map<Long, String> packedToFieldName) throws IOException
	{
		Map<Integer, String> innerClassConstants = new HashMap<>();
		Map<Integer, String> flatConstants = new HashMap<>();
		Map<String, String> innerClassRefs = new HashMap<>();
		Map<String, String> flatRefs = new HashMap<>();
		Map<Long, String> packedChildRefs = new HashMap<>();

		String currentInnerClass = null;
		Pattern innerClassPattern = Pattern.compile("\\s*public\\s+static\\s+(?:final\\s+)?class\\s+(\\w+)");
		Pattern constantPattern = Pattern.compile(
			"\\s*public\\s+static\\s+final\\s+int\\s+(\\w+)\\s*=\\s*(-?(?:0x[0-9a-fA-F_]+|\\d+))\\s*;");

		try (Scanner scanner = new Scanner(new URL(url).openStream()))
		{
			int braceDepth = 0;
			while (scanner.hasNextLine())
			{
				String line = scanner.nextLine();

				if (line.contains("*") || line.trim().startsWith("//"))
				{
					continue;
				}

				Matcher classMatcher = innerClassPattern.matcher(line);
				if (classMatcher.find())
				{
					currentInnerClass = classMatcher.group(1);
					braceDepth = 1;
					continue;
				}

				if (currentInnerClass != null)
				{
					for (char c : line.toCharArray())
					{
						if (c == '{')
						{
							braceDepth++;
						}
						if (c == '}')
						{
							braceDepth--;
						}
					}
					if (braceDepth <= 0)
					{
						currentInnerClass = null;
						continue;
					}
				}

				Matcher constMatcher = constantPattern.matcher(line);
				if (constMatcher.find())
				{
					String name = constMatcher.group(1);
					int value = parseConstantValue(constMatcher.group(2));

					if (currentInnerClass != null)
					{
						String combinedName = currentInnerClass + "_" + name;
						innerClassConstants.put(value, combinedName);
						String ref = "InterfaceID." + currentInnerClass + "." + name;
						innerClassRefs.put(combinedName, ref);
						packedChildRefs.put((long) value, ref);
					}
					else
					{
						flatConstants.put(value, name);
						flatRefs.put(name, "InterfaceID." + name);
					}
				}
			}
		}

		reverseMap.putAll(innerClassConstants);
		reverseMap.putAll(flatConstants);
		nameToRef.putAll(innerClassRefs);
		nameToRef.putAll(flatRefs);

		for (var entry : packedChildRefs.entrySet())
		{
			long packedValue = entry.getKey();
			String ref = entry.getValue();
			int ifId = (int) (packedValue >> 16);
			String ifName = flatConstants.get(ifId);
			if (ifName != null)
			{
				String fieldName = ref.substring(ref.lastIndexOf('.') + 1);
				childRefs.put(ifName + "_" + fieldName, ref);
				packedToFieldName.put(packedValue, fieldName);
			}
		}
	}

	private static int parseConstantValue(String value)
	{
		String cleaned = value.replace("_", "");
		if (cleaned.startsWith("0x") || cleaned.startsWith("0X"))
		{
			return (int) Long.parseLong(cleaned.substring(2), 16);
		}
		return Integer.parseInt(cleaned);
	}

	private static String buildPackVarsSource(List<String> interfaceEntries, List<String> childrenEntries) throws IOException
	{
		String template = Files.readString(Paths.get(TEMPLATE_PATH), StandardCharsets.UTF_8);

		StringBuilder entries = new StringBuilder();
		for (String entry : interfaceEntries)
		{
			entries.append(entry).append("\n");
		}
		for (String entry : childrenEntries)
		{
			entries.append(entry).append("\n");
		}

		return template.replace("{{ENTRIES}}", entries.toString());
	}
}
