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

package melky.resourcepacks.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import melky.resourcepacks.model.SpriteOverride;

@Slf4j
public class SpriteFileUtils
{
	/**
	 * Resolves the source sprite file path for the given override.
	 *
	 * @param spriteDir the directory containing source sprites
	 * @param override  the sprite override
	 * @return the resolved source sprite path
	 */
	public static Path resolveSourceSprite(Path spriteDir, SpriteOverride override)
	{
		String fileName = override.getSpriteID() + "-" + (override.getFrameID() != -1 ? override.getFrameID() : 0) + ".png";
		return spriteDir.resolve(fileName);
	}

	/**
	 * Resolves the destination sprite file path for the given override.
	 *
	 * @param outputDir the output directory
	 * @param override  the sprite override
	 * @return the resolved destination sprite path
	 */
	public static Path resolveDestinationSprite(Path outputDir, SpriteOverride override)
	{
		String folderName = override.getFolder().toString().toLowerCase();
		String fileName = override.toString().toLowerCase().replaceFirst(folderName + "_", "") + ".png";
		return outputDir.resolve(folderName).resolve(fileName);
	}

	/**
	 * Copies the source sprite to the destination if they differ in content.
	 * Creates parent directories of the destination if they do not exist.
	 *
	 * @param source the source sprite path
	 * @param dest   the destination sprite path
	 * @return true if the file was copied, false if it was already up-to-date
	 * @throws IOException if an I/O error occurs
	 */
	public static boolean copySpriteIfDifferent(Path source, Path dest) throws IOException
	{
		if (!Files.exists(source))
		{
			return false;
		}

		if (!Files.exists(dest) || !fileContentEquals(source, dest))
		{
			createDirectories(dest.getParent());
			Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
			return true;
		}

		return false;
	}

	/**
	 * Compares the content of two files for equality using Guava's Files.equal().
	 *
	 * @param file1 the first file
	 * @param file2 the second file
	 * @return true if the files have identical content
	 * @throws IOException if an I/O error occurs
	 */
	public static boolean fileContentEquals(Path file1, Path file2) throws IOException
	{
		return com.google.common.io.Files.equal(file1.toFile(), file2.toFile());
	}

	/**
	 * Creates directories if they do not exist, using NIO Files.createDirectories().
	 *
	 * @param dir the directory path to create
	 * @return the created directory path
	 * @throws IOException if an I/O error occurs
	 */
	public static Path createDirectories(Path dir) throws IOException
	{
		if (!Files.exists(dir))
		{
			Files.createDirectories(dir);
		}
		return dir;
	}

	/**
	 * Resolves a sprite file path to its corresponding SpriteOverride enum value.
	 * Uses the parent folder name and file name to determine the override.
	 *
	 * @param file the sprite file path
	 * @return the corresponding SpriteOverride
	 * @throws IllegalArgumentException if no matching SpriteOverride exists
	 */
	public static SpriteOverride fileToOverride(Path file)
	{
		String parentFolder = file.getParent().getFileName().toString().toLowerCase();
		String fileName = file.getFileName().toString().replace(".png", "");

		if ("other".equals(parentFolder))
		{
			return SpriteOverride.valueOf(fileName.toUpperCase());
		}

		return SpriteOverride.valueOf((parentFolder + "_" + fileName).toUpperCase());
	}
}
