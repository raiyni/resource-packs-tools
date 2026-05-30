/*
 * Copyright (c) 2024, Melky <https://github.com/melkypie>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
package io.ryoung;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

@Slf4j
public class JavaDownloaderPlugin implements Plugin<Project>
{
	@Override
	public void apply(Project project)
	{
		JavaDownloadExtension extension = project.getExtensions()
			.create("javaDownload", JavaDownloadExtension.class);

		extension.getSourceUrl().convention("");
		extension.getTargetPath().convention("");

		TaskProvider<DownloadJavaTask> downloadTask = project.getTasks()
			.register("downloadJavaFiles", DownloadJavaTask.class, task ->
			{
				task.getSourceUrl().set(extension.getSourceUrl());
				task.getTargetPath().set(extension.getTargetPath());
				task.getFiles().set(extension.getFiles());
			});

		project.getTasks()
			.findByName("clean")
			.doLast((a) ->
			{
				try
				{
					List<Map<String, Object>> allFiles = new ArrayList<>();

					if (extension.getFiles().isPresent())
					{
						allFiles.addAll(extension.getFiles().get());
					}

					if (!extension.getSourceUrl().get().isEmpty()
						&& !extension.getTargetPath().get().isEmpty())
					{
						allFiles.add(Map.of("url", extension.getSourceUrl().get(),
							"path", extension.getTargetPath().get()));
					}

					for (Map<String, Object> file : allFiles)
					{
						File targetFile = project.file(file.get("path").toString());
						Files.deleteIfExists(targetFile.toPath());
					}
				}
				catch (IOException e)
				{
					throw new RuntimeException(e);
				}
			});

		project.getTasks()
			.withType(JavaCompile.class)
			.forEach(t -> t.dependsOn(downloadTask));

		project.afterEvaluate(p ->
		{
			log.info("JavaDownloaderPlugin: Configuring downloads for IDE sync...");

			DownloadJavaTask task = downloadTask.get();

			List<Map<String, Object>> allFiles = new ArrayList<>();

			if (extension.getFiles().isPresent() && !extension.getFiles().get().isEmpty())
			{
				allFiles.addAll(extension.getFiles().get());
			}

			if (!extension.getSourceUrl().get().isEmpty()
				&& !extension.getTargetPath().get().isEmpty())
			{
				allFiles.add(Map.of("url", extension.getSourceUrl().get(),
					"path", extension.getTargetPath().get()));
			}

			if (allFiles.isEmpty())
			{
				log.info("JavaDownloaderPlugin: No files configured for download");
				return;
			}

			for (Map<String, Object> file : allFiles)
			{
				String filePath = file.get("path").toString();
				String fileUrl = file.get("url").toString();
				File targetFile = project.file(filePath);
				if (!targetFile.exists())
				{
					try
					{
						task.downloadFile(fileUrl, filePath);
					}
					catch (Exception e)
					{
						log.error("Download during configuration failed: {}", e.getMessage());
					}
				}
				else
				{
					log.info("Java file already exists: {}", targetFile.getName());
				}
			}

			var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
			sourceSets.getByName("main").getJava().srcDir(project.file("src/main/java"));
		});
	}
}
