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

import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import melky.resourcepacks.common.DirectoryWalker;
import melky.resourcepacks.common.ReportWriter;
import melky.resourcepacks.common.SpriteFileUtils;
import melky.resourcepacks.model.SpriteOverride;

@Slf4j
public class SpriteSyncer
{
	private static final List<String> errors = new ArrayList<>();
	private static final List<String> warnings = new ArrayList<>();

	public static void main(String[] args) throws IOException
	{
		syncSprites();
		writeReports();
	}

	public static void syncSprites() throws IOException
	{
		String spriteFolder = System.getProperty("spriteFolder");
		String outputDir = System.getProperty("outputDir", "sample-pack");
		if (Strings.isNullOrEmpty(spriteFolder))
		{
			throw new RuntimeException("spriteFolder needs to be defined");
		}

		Path spriteDir = Paths.get(spriteFolder);
		Path outputPath = Paths.get(outputDir);

		for (SpriteOverride override : SpriteOverride.values())
		{
			if (override.getSpriteID() < 0)
			{
				continue;
			}

			Path sourceSprite = SpriteFileUtils.resolveSourceSprite(spriteDir, override);
			Path destinationSprite = SpriteFileUtils.resolveDestinationSprite(outputPath, override);

			if (SpriteFileUtils.copySpriteIfDifferent(sourceSprite, destinationSprite))
			{
				log.info("Updated sprite " + override.name() + " (" + override.getSpriteID() + ")");
			}
		}

		DirectoryWalker.walkDirectory(outputPath, spriteDir, true, warnings, errors);
	}

	public static void writeReports() throws IOException
	{
		String outputDir = System.getProperty("outputDir", "sample-pack");
		ReportWriter.writeReports(Path.of(outputDir), errors, warnings);
	}
}
