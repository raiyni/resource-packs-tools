/*
 * Copyright (c) 2026, Ron young <https://github.com/raiyni>
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultVarsExtractor
{
	private static final String OVERRIDES_TOML_PATH = "src/main/resources/overrides/overrides.toml";
	private static final String VARS_TOML_PATH = "src/main/resources/overrides/vars.toml";
	private static final int MIN_FREQUENCY = 1;

	private static final Pattern SECTION_PATTERN = Pattern.compile("^\\[\\[?([^]]+)\\]?.*$");
	private static final Pattern COLOR_PATTERN = Pattern.compile("^color=(.+)$");
	private static final Pattern OPACITY_PATTERN = Pattern.compile("^opacity=(.+)$");
	private static final Pattern TEMPLATE_PATTERN = Pattern.compile("^\"\\$\\{([\\w.]+)}\"$");
	private static final Pattern HEX_VALUE_PATTERN = Pattern.compile("^0x[0-9a-fA-F]+$");
	private static final Pattern DECIMAL_VALUE_PATTERN = Pattern.compile("^-?\\d+$");

	enum ValueType
	{
		COLOR,
		OPACITY
	}

	@Value
	static class ValueKey
	{
		long value;
		ValueType type;
	}

	public static void main(String[] args) throws Exception
	{
		log.info("Reading {}...", OVERRIDES_TOML_PATH);
		String overridesContent = Files.readString(Paths.get(OVERRIDES_TOML_PATH), StandardCharsets.UTF_8);

		Map<String, Long> existingVars = loadExistingVars();
		log.info("Loaded {} existing vars for template resolution", existingVars.size());

		Map<ValueKey, Integer> globalFrequency = new HashMap<>();
		Map<ValueKey, Map<String, Integer>> suffixCounts = new HashMap<>();
		Map<ValueKey, Map<String, Integer>> valueContexts = new HashMap<>();

		extractValues(overridesContent, existingVars, globalFrequency, suffixCounts, valueContexts);

		log.info("Found {} unique values", globalFrequency.size());

		List<Map.Entry<ValueKey, Integer>> sorted = globalFrequency.entrySet().stream()
			.filter(e -> e.getValue() >= MIN_FREQUENCY)
			.sorted((a, b) ->
			{
				int cmp = b.getValue().compareTo(a.getValue());
				if (cmp != 0)
				{
					return cmp;
				}
				return a.getKey().toString().compareTo(b.getKey().toString());
			})
			.collect(Collectors.toList());

		log.info("Values with frequency >= {}: {}", MIN_FREQUENCY, sorted.size());

		Map<String, Map<String, Integer>> varContexts = new HashMap<>();
		Map<String, Integer> varFrequencies = new HashMap<>();
		Map<String, Long> vars = generateVars(sorted, suffixCounts, valueContexts, varContexts, varFrequencies, existingVars);

		for (var entry : vars.entrySet())
		{
			String name = entry.getKey();
			long value = entry.getValue();
			Integer frequency = varFrequencies.get(name);
			Map<String, Integer> contexts = varContexts.get(name);

			String valueStr = name.startsWith("opacity.") ? String.valueOf(value) : formatValue(value);
			StringBuilder logLine = new StringBuilder();
			logLine.append(name).append("=").append(valueStr);

			if (frequency != null)
			{
				logLine.append(" — ").append(frequency);
			}

			if (contexts != null && !contexts.isEmpty())
			{
				String contextStr = contexts.entrySet().stream()
					.sorted(Map.Entry.comparingByKey())
					.map(e -> e.getKey() + " (" + e.getValue() + ")")
					.collect(Collectors.joining(", "));
				logLine.append(" [").append(contextStr).append("]");
			}

			log.info("{}", logLine);
		}

		Map<String, Long> colorVars = new TreeMap<>();
		Map<String, Long> opacityVars = new TreeMap<>();

		for (var entry : vars.entrySet())
		{
			String name = entry.getKey();
			if (name.startsWith("opacity."))
			{
				opacityVars.put(name.substring("opacity.".length()), entry.getValue());
			}
			else if (name.startsWith("color."))
			{
				colorVars.put(name.substring("color.".length()), entry.getValue());
			}
		}

		StringBuilder output = new StringBuilder();
		if (!colorVars.isEmpty())
		{
			output.append("[color]\n");
			for (var entry : colorVars.entrySet())
			{
				String key = entry.getKey();
				appendWithContext(output, "color." + key, entry.getValue(), true, varContexts);
			}
		}

		if (!opacityVars.isEmpty())
		{
			if (!colorVars.isEmpty())
			{
				output.append("\n");
			}
			output.append("[opacity]\n");
			for (var entry : opacityVars.entrySet())
			{
				String key = entry.getKey();
				appendWithContext(output, "opacity." + key, entry.getValue(), false, varContexts);
			}
		}

		Path outputPath = Paths.get(VARS_TOML_PATH);
		Files.createDirectories(outputPath.getParent());
		Files.writeString(outputPath, output.toString());

		log.info("Generated {} with {} entries ({} color, {} opacity)",
			VARS_TOML_PATH, vars.size(), colorVars.size(), opacityVars.size());

		rewriteOverrides(overridesContent, vars, existingVars);
	}

	static void rewriteOverrides(String content, Map<String, Long> vars, Map<String, Long> existingVars)
	{
		Map<ValueKey, String> reverseMap = new HashMap<>();
		for (var entry : vars.entrySet())
		{
			String name = entry.getKey();
			long value = entry.getValue();
			ValueType type = name.startsWith("opacity.") ? ValueType.OPACITY : ValueType.COLOR;
			reverseMap.put(new ValueKey(value, type), name);
		}

		String[] lines = content.split("\n", -1);
		List<String> output = new ArrayList<>();
		int replacements = 0;

		for (String line : lines)
		{
			String trimmed = line.trim();

			Matcher sectionMatcher = SECTION_PATTERN.matcher(trimmed);
			if (sectionMatcher.matches())
			{
				output.add(line);
				continue;
			}

			Matcher colorMatcher = COLOR_PATTERN.matcher(trimmed);
			if (colorMatcher.matches())
			{
				String valueStr = colorMatcher.group(1).trim();
				Long rawValue = resolveValue(valueStr, existingVars);
				if (rawValue != null)
				{
					String varName = reverseMap.get(new ValueKey(rawValue, ValueType.COLOR));
					if (varName != null)
					{
						String leading = line.substring(0, line.indexOf(trimmed));
						output.add(leading + "color=\"${" + varName + "}\"");
						replacements++;
						continue;
					}
				}
				output.add(line);
				continue;
			}

			Matcher opacityMatcher = OPACITY_PATTERN.matcher(trimmed);
			if (opacityMatcher.matches())
			{
				String valueStr = opacityMatcher.group(1).trim();
				Long rawValue = resolveValue(valueStr, existingVars);
				if (rawValue != null)
				{
					String varName = reverseMap.get(new ValueKey(rawValue, ValueType.OPACITY));
					if (varName != null)
					{
						String leading = line.substring(0, line.indexOf(trimmed));
						output.add(leading + "opacity=\"${" + varName + "}\"");
						replacements++;
						continue;
					}
				}
				output.add(line);
				continue;
			}

			output.add(line);
		}

		try
		{
			Files.writeString(Paths.get(OVERRIDES_TOML_PATH), String.join("\n", output));
			log.info("Rewrote {} with {} template replacements", OVERRIDES_TOML_PATH, replacements);
		}
		catch (Exception e)
		{
			log.warn("Warning: could not write overrides.toml: {}", e.getMessage());
		}
	}

	static Map<String, Long> loadExistingVars()
	{
		Map<String, Long> vars = new HashMap<>();
		Path varsPath = Paths.get(VARS_TOML_PATH);
		if (!Files.exists(varsPath))
		{
			return vars;
		}

		try
		{
			String content = Files.readString(varsPath, StandardCharsets.UTF_8);
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

				int eq = trimmed.indexOf('=');
				if (eq < 0)
				{
					continue;
				}

				String key = trimmed.substring(0, eq).trim();
				String valueStr = trimmed.substring(eq + 1).trim();
				Long value = parseValue(valueStr);
				if (value != null)
				{
					String fullKey = currentSection != null ? currentSection + "." + key : key;
					vars.put(fullKey, value);
				}
			}
		}
		catch (Exception e)
		{
			log.warn("Warning: could not read existing vars.toml: {}", e.getMessage());
		}

		return vars;
	}

	static void extractValues(
		String content,
		Map<String, Long> existingVars,
		Map<ValueKey, Integer> globalFrequency,
		Map<ValueKey, Map<String, Integer>> suffixCounts,
		Map<ValueKey, Map<String, Integer>> valueContexts)
	{
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

			Matcher colorMatcher = COLOR_PATTERN.matcher(trimmed);
			if (colorMatcher.matches())
			{
				Long rawValue = resolveValue(colorMatcher.group(1).trim(), existingVars);
				if (rawValue != null)
				{
					ValueKey key = new ValueKey(rawValue, ValueType.COLOR);
					globalFrequency.merge(key, 1, Integer::sum);
					if (currentSection != null)
					{
						String suffix = extractContextSuffix(currentSection);
						suffixCounts.computeIfAbsent(key, k -> new HashMap<>())
							.merge(suffix, 1, Integer::sum);
						String context = extractFirstComponent(currentSection);
						valueContexts.computeIfAbsent(key, k -> new HashMap<>()).merge(context, 1, Integer::sum);
					}
				}
				continue;
			}

			Matcher opacityMatcher = OPACITY_PATTERN.matcher(trimmed);
			if (opacityMatcher.matches())
			{
				Long rawValue = resolveValue(opacityMatcher.group(1).trim(), existingVars);
				if (rawValue != null)
				{
					ValueKey key = new ValueKey(rawValue, ValueType.OPACITY);
					globalFrequency.merge(key, 1, Integer::sum);
					if (currentSection != null)
					{
						String context = extractFirstComponent(currentSection);
						valueContexts.computeIfAbsent(key, k -> new HashMap<>()).merge(context, 1, Integer::sum);
					}
				}
			}
		}
	}

	static Long resolveValue(String valueStr, Map<String, Long> existingVars)
	{
		Matcher templateMatcher = TEMPLATE_PATTERN.matcher(valueStr);
		if (templateMatcher.matches())
		{
			String varName = templateMatcher.group(1);
			return existingVars.get(varName);
		}

		return parseValue(valueStr);
	}

	static Long parseValue(String valueStr)
	{
		if (HEX_VALUE_PATTERN.matcher(valueStr).matches())
		{
			return Long.parseLong(valueStr.substring(2), 16);
		}
		if (DECIMAL_VALUE_PATTERN.matcher(valueStr).matches())
		{
			return Long.parseLong(valueStr);
		}
		return null;
	}

	static String extractContextSuffix(String section)
	{
		if (section.isEmpty())
		{
			return section;
		}

		String[] parts = section.split("\\.");
		if (parts.length <= 1)
		{
			return section;
		}

		List<String> suffixParts = new ArrayList<>();
		for (int i = parts.length - 1; i >= 0; i--)
		{
			suffixParts.add(0, parts[i]);
			String candidate = String.join(".", suffixParts);
			if (isContextualSuffix(candidate))
			{
				return candidate;
			}
		}

		return parts[parts.length - 1];
	}

	static boolean isContextualSuffix(String suffix)
	{
		return suffix.contains("border")
			|| suffix.contains("separator")
			|| suffix.contains("background")
			|| suffix.contains("overlay")
			|| suffix.contains("menu")
			|| suffix.contains("dropdown")
			|| suffix.contains("header")
			|| suffix.contains("tab")
			|| suffix.contains("popup")
			|| suffix.contains("rectangle");
	}

	static String mostCommonSuffix(Map<String, Integer> counts)
	{
		return counts.entrySet().stream()
			.sorted((a, b) ->
			{
				int cmp = b.getValue().compareTo(a.getValue());
				if (cmp != 0)
				{
					return cmp;
				}
				return a.getKey().compareTo(b.getKey());
			})
			.findFirst()
			.map(Map.Entry::getKey)
			.orElse("unknown");
	}

	static Map<String, Long> generateVars(
		List<Map.Entry<ValueKey, Integer>> sorted,
		Map<ValueKey, Map<String, Integer>> suffixCounts,
		Map<ValueKey, Map<String, Integer>> valueContexts,
		Map<String, Map<String, Integer>> varContexts,
		Map<String, Integer> varFrequencies,
		Map<String, Long> existingVars)
	{
		Map<String, Long> vars = new LinkedHashMap<>();
		Set<String> usedNames = new HashSet<>();

		Map<ValueKey, String> reverseMap = new HashMap<>();
		for (var entry : existingVars.entrySet())
		{
			String name = entry.getKey();
			long value = entry.getValue();
			ValueType type = name.startsWith("opacity.") ? ValueType.OPACITY : ValueType.COLOR;
			reverseMap.put(new ValueKey(value, type), name);
		}

		for (var entry : sorted)
		{
			ValueKey key = entry.getKey();
			String existingName = reverseMap.get(key);
			String name;

			if (existingName != null && !usedNames.contains(existingName))
			{
				name = existingName;
			}
			else if (key.getType() == ValueType.OPACITY)
			{
				name = "opacity." + deriveOpacityName(key.getValue());
				name = ensureUnique(name, usedNames);
			}
			else
			{
				Map<String, Integer> suffixes = suffixCounts.get(key);
				String suffix = suffixes != null && !suffixes.isEmpty()
					? mostCommonSuffix(suffixes)
					: "unknown";
				name = "color." + suffix;
				name = ensureUnique(name, usedNames);
			}

			usedNames.add(name);
			vars.put(name, key.getValue());

			Map<String, Integer> contexts = valueContexts.get(key);
			if (contexts != null)
			{
				varContexts.put(name, new TreeMap<>(contexts));
			}
			varFrequencies.put(name, entry.getValue());
		}

		return vars;
	}

	static String deriveOpacityName(long value)
	{
		if (value == 0)
		{
			return "opaque";
		}
		if (value == 255)
		{
			return "transparent";
		}
		if (value <= 50)
		{
			return "mostly_opaque";
		}
		if (value <= 100)
		{
			return "dimmed";
		}
		if (value <= 150)
		{
			return "translucent";
		}
		if (value <= 200)
		{
			return "semi_transparent";
		}
		return "mostly_transparent";
	}

	static String ensureUnique(String name, Set<String> usedNames)
	{
		if (!usedNames.contains(name))
		{
			return name;
		}

		String base = name;
		int suffix = 2;
		while (usedNames.contains(base + "_" + suffix))
		{
			suffix++;
		}
		return base + "_" + suffix;
	}

	static String formatValue(long value)
	{
		if (value >= 0 && value <= 0xFFFFFF)
		{
			return "0x" + Long.toHexString(value);
		}
		return String.valueOf(value);
	}

	static String extractFirstComponent(String section)
	{
		int dot = section.indexOf('.');
		return dot > 0 ? section.substring(0, dot) : section;
	}

	static void appendWithContext(
		StringBuilder output,
		String fullName,
		long value,
		boolean isColor,
		Map<String, Map<String, Integer>> varContexts)
	{
		Map<String, Integer> contexts = varContexts.get(fullName);
		if (contexts != null && !contexts.isEmpty())
		{
			String contextStr = contexts.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getKey)
				.collect(Collectors.joining(", "));
			output.append("# ").append(contextStr).append("\n");
		}
		String valueStr = isColor ? formatValue(value) : String.valueOf(value);
		String key = fullName.contains(".") ? fullName.substring(fullName.indexOf('.') + 1) : fullName;
		output.append(key).append("=").append(valueStr).append("\n");
	}
}
