/*
 * Copyright (c) 2025, Ron Young <https://github.com/raiyni>
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

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.google.common.io.Files;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import melky.resourcepacks.model.runelite.ChatColorKey;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

@Slf4j
public class SampleGenerator
{
	private static String CHAT_COLORS_COMMENT = "# chat_colors requires Allow chat colors to be changed\n" +
		"# Unused lines can be deleted or commented out with #\n";

	public static void main(String[] args)
	{
		createSample();
		createSampleMinified();
		createChatColors();
	}

	public static void addKeys(final TomlTable table, Collection<String> keys, Multimap<String, String> values, String path)
	{
		for (var k : keys)
		{
			var s = k.replaceAll("\\.?color|\\.?opacity", "");
			if (k.endsWith("color"))
			{
				if (table.isLong(k))
				{
					values.put(path + s, String.format("# color=0x%06x", new Color(table.getLong(k).intValue()).getRGB() & 16777215));
				}
				else if (table.isString(k))
				{

					values.put(path + s, String.format("# color=\"%s\"", table.get(k)));
				}
			}
			else if (k.endsWith("opacity"))
			{
				if (table.isLong(k))
				{
					values.put(path + s, String.format("# opacity=%d", table.getLong(k).intValue()));
				}
				else if (table.isString(k))
				{

					values.put(path + s, String.format("# opacity=\"%s\"", table.get(k)));
				}
			}
		}
	}

	static InputStream getOverridesStream() throws IOException
	{
		String overridesPath = System.getProperty("overridesPath");
		if (overridesPath != null && !overridesPath.isEmpty())
		{
			return java.nio.file.Files.newInputStream(Paths.get(overridesPath));
		}
		return SampleGenerator.class.getResourceAsStream("/overrides/overrides.toml");
	}

	public static void createSample()
	{
		try (var stream = getOverridesStream())
		{
			assert stream != null;

			TomlParseResult toml = Toml.parse(stream);
			toml.errors().forEach(error -> log.error(error.toString()));

			var keys = toml.dottedKeySet()
				.stream()
				.filter(k -> k.contains("color") || k.contains("opacity"))
				.sorted()
				.collect(Collectors.toList());

			var lists = toml.dottedKeySet()
				.stream()
				.filter(k -> !(k.contains("scripts") || k.contains("dynamicChildren") || k.contains("children")) && toml.isArray(k))
				.sorted()
				.collect(Collectors.toList());

			var sb = new StringBuilder();
			sb.append("# remove comments (#) on lines to see changes affected\n")
				.append("# overlay color is in ARGB hex format\n")
				.append("# [overlay]\n")
				.append("# color=0x9C463D32\n");

			Multimap<String, String> tables = TreeMultimap.create();
			addKeys(toml, keys, tables, "");

			for (var l : lists)
			{
				TomlArray a = toml.getArray(l);
				assert a != null;

				for (var o : a.toList())
				{
					if (o instanceof TomlTable)
					{
						var t = (TomlTable) o;
						addKeys(t, t.keySet(), tables, l);
					}
				}
			}

			for (var k : tables.keySet())
			{
				sb.append(String.format("\n# [%s]\n", k));
				sb.append(String.join("\n", tables.get(k)));
				sb.append("\n");
			}

			log.info("{}", sb + "");

			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			var selection = new StringSelection((sb + ""));
			clipboard.setContents(selection, null);

			if (!Strings.isNullOrEmpty(System.getProperty("sampleOutput")))
			{
				File file = new File(System.getProperty("sampleOutput"));
				Files.write(sb + "", file, Charsets.UTF_8);
			}
		}
		catch (IOException e)
		{
			log.error("error loading overrides", e);
		}
	}

	public static void createSampleMinified()
	{
		try (var stream = getOverridesStream())
		{
			assert stream != null;

			TomlParseResult toml = Toml.parse(stream);
			toml.errors().forEach(error -> log.error(error.toString()));

			var keys = toml.dottedKeySet()
				.stream()
				.filter(k -> k.contains("color") || k.contains("opacity"))
				.sorted()
				.collect(Collectors.toList());

			var lists = toml.dottedKeySet()
				.stream()
				.filter(k -> !(k.contains("scripts") || k.contains("dynamicChildren") || k.contains("children")) && toml.isArray(k))
				.sorted()
				.collect(Collectors.toList());

			var sb = new StringBuilder();
			sb.append("# remove comments (#) on lines to see changes affected\n")
				.append("# overlay color is in ARGB hex format\n")
				.append("# overlay.color=0x9C463D32\n\n");

			Multimap<String, String> tables = TreeMultimap.create();
			addKeys(toml, keys, tables, "");

			for (var l : lists)
			{
				TomlArray a = toml.getArray(l);
				assert a != null;

				for (var o : a.toList())
				{
					if (o instanceof TomlTable)
					{
						var t = (TomlTable) o;
						addKeys(t, t.keySet(), tables, l);
					}
				}
			}

			for (var k : tables.keySet())
			{
				sb.append(tables.get(k)
					.stream()
					.map(s -> String.format("# %s.%s", k, s.replace("# ", "")))
					.collect(Collectors.joining("\n")));
				sb.append("\n\n");
			}

			log.info("{}", sb + "");

			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			var selection = new StringSelection((sb + ""));
			clipboard.setContents(selection, null);

			if (!Strings.isNullOrEmpty(System.getProperty("sampleMinifiedOutput")))
			{
				File file = new File(System.getProperty("sampleMinifiedOutput"));
				Files.write(sb + "", file, Charsets.UTF_8);
			}
		}
		catch (IOException e)
		{
			log.error("error loading overrides", e);
		}
	}

	public static void createChatColors()
	{
		try
		{
			var sb = new StringBuilder();
			sb.append("# chat_colors requires Allow chat colors to be enabled in settings\n")
				.append("# missing values will use the default RuneLight value\n")
				.append("# remove comments (#) on lines to see changes affected\n");

			sb.append("\n[" + ChatColorKey.OPAQUE_KEY + "]\n");
			for (var c : ChatColorKey.values())
			{
				sb.append("# ")
					.append(c.toOverrideKey())
					.append("=\n");
			}

			sb.append("\n[" + ChatColorKey.TRANSPARENT_KEY + "]\n");
			for (var c : ChatColorKey.values())
			{
				sb.append("# ")
					.append(c.toOverrideKey())
					.append("=\n");
			}

			log.info("{}", sb + "");

			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			var selection = new StringSelection((sb + ""));
			clipboard.setContents(selection, null);

			if (!Strings.isNullOrEmpty(System.getProperty("chatColorsOutput")))
			{
				File file = new File(System.getProperty("chatColorsOutput"));
				Files.write(sb + "", file, Charsets.UTF_8);
			}
		}
		catch (IOException e)
		{
			log.error("error writing chat colors", e);
		}
	}
}
