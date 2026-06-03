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

package melky.resourcepacks.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import melky.resourcepacks.model.SpriteOverride;

@Slf4j
public class DirectoryWalker
{
	/**
	 * Recursively walks a directory tree, validating sprite files against source sprites.
	 * Optionally deletes files that do not match a valid SpriteOverride.
	 *
	 * @param directory the directory to walk
	 * @param spriteDir the directory containing source sprites for comparison
	 * @param delete    if true, deletes files that don't match a valid SpriteOverride; if false, reports warnings
	 * @param warnings  list to collect warning messages
	 * @param errors    list to collect error messages
	 * @return a list of error messages encountered during traversal
	 * @throws IOException if an I/O error occurs
	 */
	public static List<String> walkDirectory(Path directory, Path spriteDir, boolean delete, List<String> warnings, List<String> errors) throws IOException
	{
		List<String> errorMessages = new ArrayList<>();
		String dirName = directory.getFileName().toString();

		if (dirName.equals(".git"))
		{
			return errorMessages;
		}

		if (!Files.exists(directory))
		{
			return errorMessages;
		}

		try (Stream<Path> entries = Files.list(directory))
		{
			List<Path> entryList = entries.collect(Collectors.toList());

			if (entryList.isEmpty())
			{
				warn("\u001B[33mDirectory " + dirName + " is not needed as it is empty\u001B[0m", warnings);
				return errorMessages;
			}

			for (Path entry : entryList)
			{
				if (Files.isDirectory(entry))
				{
					errorMessages.addAll(walkDirectory(entry, spriteDir, delete, warnings, errors));
				}
				else
				{
					String fileName = entry.getFileName().toString();

					if (fileName.contains(".png") && !fileName.equals("icon.png"))
					{
						processSpriteFile(entry, dirName, spriteDir, delete, warnings);
					}
					else if (!fileName.contains(".properties") && !fileName.contains(".toml") && !fileName.contains(".md") && !fileName.equals("icon.png"))
					{
						errorMessages.add("\u001B[31mFound a file " + fileName + " in folder " + dirName + " that is not a sprite, icon, properties or markdown file\u001B[0m");
					}
				}
			}
		}

		return errorMessages;
	}

	private static void processSpriteFile(Path file, String dirName, Path spriteDir, boolean delete, List<String> warnings) throws IOException
	{
		try
		{
			SpriteOverride override = SpriteFileUtils.fileToOverride(file);

			if (override.getSpriteID() < 0)
			{
				return;
			}

			Path originalSprite = SpriteFileUtils.resolveSourceSprite(spriteDir, override);

			if (!delete && Files.exists(originalSprite) && SpriteFileUtils.fileContentEquals(file, originalSprite))
			{
				warn("\u001B[33mFile " + file.getFileName() + " (" + override.getSpriteID() + ") in folder " + dirName + " is the same as the vanilla sprite\u001B[0m", warnings);
			}
		}
		catch (IllegalArgumentException e)
		{
			if (delete)
			{
				Files.deleteIfExists(file);
				log.info("Deleted missing file: {}/{}", dirName, file.getFileName());
			}
			else
			{
				warn("\u001B[33mFile " + file.getFileName() + " in folder " + dirName + " is redundant\u001B[0m", warnings);
			}
		}
	}

	private static void warn(String msg, List<String> warnings)
	{
		log.warn(msg);
		warnings.add(msg);
	}
}
