/*
 * Copyright (c) 2025, Ron young <https://github.com/raiyni>
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

package melky.resourcepacks.validation;

import com.google.common.base.Strings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import melky.resourcepacks.common.DirectoryWalker;
import melky.resourcepacks.common.ReportWriter;
import melky.resourcepacks.common.SpriteFileUtils;
import melky.resourcepacks.model.SpriteOverride;

@Slf4j
public class PackCheck
{
	private static final List<String> errors = new ArrayList<>();
	private static final List<String> warnings = new ArrayList<>();

	public static void main(String[] args) throws IOException
	{
		String spriteFolder = System.getProperty("spriteFolder");
		String packFolder = System.getProperty("packFolder");
		if (Strings.isNullOrEmpty(spriteFolder) || Strings.isNullOrEmpty(packFolder))
		{
			throw new RuntimeException("spriteFolder and packFolder need to be defined");
		}

		try
		{
			checkUnneededFiles(spriteFolder, packFolder);
			checkPackProperties(packFolder);
			moveImages(spriteFolder, packFolder);
		}
		finally
		{
			String basePath = System.getProperty("user.dir");
			ReportWriter.writeReports(Paths.get(basePath), errors, warnings);
		}
	}

	private static void checkUnneededFiles(String spriteFolder, String packFolder) throws IOException
	{
		Path packFolderPath = Paths.get(packFolder);
		Path spriteDir = Paths.get(spriteFolder);
		var errorMessages = DirectoryWalker.walkDirectory(packFolderPath, spriteDir, false, warnings, errors);
		if (!errorMessages.isEmpty())
		{
			throw new IllegalArgumentException(String.join("\n", errorMessages));
		}
	}

	private static void checkPackProperties(String packFolder) throws IOException
	{
		Path propertiesFile = Paths.get(packFolder, "pack.properties");
		if (!Files.exists(propertiesFile))
		{
			throw new IllegalArgumentException("Pack does not contain a pack.properties file");
		}

		Properties properties = new Properties();
		try (InputStream is = Files.newInputStream(propertiesFile))
		{
			properties.load(is);
		}

		List<String> errorMessages = new ArrayList<>();

		if (Strings.isNullOrEmpty(properties.getProperty("displayName")))
		{
			errorMessages.add("pack.properties does not contain a displayName property");
		}

		if (Strings.isNullOrEmpty(properties.getProperty("author")))
		{
			errorMessages.add("pack.properties does not contain a author property");
		}

		if (!properties.containsKey("tags"))
		{
			errorMessages.add("pack.properties does not contain a tags property");
		}

		if (!errorMessages.isEmpty())
		{
			throw new IllegalArgumentException(String.join("\n", errorMessages));
		}
	}

	private static void moveImages(String spriteFolder, String packFolder) throws IOException
	{
		Path spriteDir = Paths.get(spriteFolder);
		Path packPath = Paths.get(packFolder);

		for (SpriteOverride override : SpriteOverride.values())
		{
			if (override.getSpriteID() < 0)
			{
				continue;
			}

			Path sourceSprite = SpriteFileUtils.resolveSourceSprite(spriteDir, override);
			Path destinationSprite = SpriteFileUtils.resolveDestinationSprite(packPath, override);

			if (SpriteFileUtils.copySpriteIfDifferent(sourceSprite, destinationSprite))
			{
				log.info("Updated sprite " + override.name() + " (" + override.getSpriteID() + ")");
			}
		}

		DirectoryWalker.walkDirectory(packPath, spriteDir, true, warnings, errors);
	}
}
