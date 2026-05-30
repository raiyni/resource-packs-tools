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

import lombok.extern.slf4j.Slf4j;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class DownloadJavaTask extends DefaultTask
{
	@Input
	public abstract Property<String> getSourceUrl();

	@Input
	public abstract Property<String> getTargetPath();

	@Input
	public abstract ListProperty<Map<String, Object>> getFiles();

	@TaskAction
	public void executeDownload()
	{
		List<Map<String, Object>> allFiles = new ArrayList<>();

		if (getFiles().isPresent() && !getFiles().get().isEmpty())
		{
			allFiles.addAll(getFiles().get());
		}

		if (getSourceUrl().isPresent() && !getSourceUrl().get().isEmpty()
			&& getTargetPath().isPresent() && !getTargetPath().get().isEmpty())
		{
			allFiles.add(Map.of("url", (Object) getSourceUrl().get(), "path", (Object) getTargetPath().get()));
		}

		if (allFiles.isEmpty())
		{
			log.info("No files configured for download");
			return;
		}

		for (Map<String, Object> file : allFiles)
		{
			downloadFile(file.get("url").toString(), file.get("path").toString());
		}
	}

	public void downloadFile(String url, String targetPath)
	{
		if (url == null || url.isEmpty())
		{
			throw new GradleException("Source URL is not configured for file: " + targetPath);
		}

		if (targetPath == null || targetPath.isEmpty())
		{
			throw new GradleException("Target path is not configured for URL: " + url);
		}

		File targetFile = getProject().file(targetPath);

		try
		{
			if (!targetFile.exists())
			{
				log.info("Downloading Java file from: {}", url);

				com.google.common.io.Files.createParentDirs(targetFile);

				HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
				connection.setRequestProperty("User-Agent", "Gradle Build Tool");
				connection.setRequestProperty("Accept", "text/plain");

				int responseCode = connection.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_OK)
				{
					try (InputStream input = connection.getInputStream())
					{
						com.google.common.io.Files.asByteSink(targetFile).writeFrom(input);
					}
					log.info("Successfully downloaded: {}", targetFile.getName());
				}
				else
				{
					throw new GradleException("Failed to download file from " + url + ". HTTP response: " + responseCode);
				}
			}
			else
			{
				log.info("Java file already exists: {}", targetFile.getName());
			}
		}
		catch (IOException e)
		{
			throw new GradleException("Failed to download Java file: " + e.getMessage(), e);
		}
	}
}
