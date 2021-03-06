/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt3.base

import org.jetbrains.kotlin.base.kapt3.KaptOptions
import org.jetbrains.kotlin.kapt3.base.util.KaptLogger
import org.jetbrains.kotlin.kapt3.base.util.info
import java.io.Closeable
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.util.*
import javax.annotation.processing.Processor

class LoadedProcessors(val processors: List<Processor>, val classLoader: ClassLoader)

open class ProcessorLoader(private val options: KaptOptions, private val logger: KaptLogger) : Closeable {
    private var annotationProcessingClassLoader: URLClassLoader? = null

    fun loadProcessors(parentClassLoader: ClassLoader = ClassLoader.getSystemClassLoader()): LoadedProcessors {
        clearJarURLCache()

        val classpath = (options.processingClasspath + options.compileClasspath).distinct()
        val classLoader = URLClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray(), parentClassLoader)
        this.annotationProcessingClassLoader = classLoader

        val processors = if (options.processors.isNotEmpty()) {
            logger.info("Annotation processor class names are set, skip AP discovery")
            options.processors.mapNotNull { tryLoadProcessor(it, classLoader) }
        } else {
            logger.info("Need to discovery annotation processors in the AP classpath")
            doLoadProcessors(classLoader)
        }

        if (processors.isEmpty()) {
            logger.info("No annotation processors available, aborting")
        } else {
            logger.info { "Annotation processors: " + processors.joinToString { it::class.java.canonicalName } }
        }

        return LoadedProcessors(processors, classLoader)
    }

    open fun doLoadProcessors(classLoader: URLClassLoader): List<Processor> {
        return ServiceLoader.load(Processor::class.java, classLoader).toList()
    }

    private fun tryLoadProcessor(fqName: String, classLoader: ClassLoader): Processor? {
        val annotationProcessorClass = try {
            Class.forName(fqName, true, classLoader)
        } catch (e: Throwable) {
            logger.warn("Can't find annotation processor class $fqName: ${e.message}")
            return null
        }

        try {
            val annotationProcessorInstance = annotationProcessorClass.newInstance()
            if (annotationProcessorInstance !is Processor) {
                logger.warn("$fqName is not an instance of 'Processor'")
                return null
            }

            return annotationProcessorInstance
        } catch (e: Throwable) {
            logger.warn("Can't load annotation processor class $fqName: ${e.message}")
            return null
        }
    }

    override fun close() {
        annotationProcessingClassLoader?.close()
        clearJarURLCache()
    }
}

// Copied from com.intellij.ide.ClassUtilCore
private fun clearJarURLCache() {
    fun clearMap(cache: Field) {
        cache.isAccessible = true

        if (!Modifier.isFinal(cache.modifiers)) {
            cache.set(null, hashMapOf<Any, Any>())
        } else {
            val map = cache.get(null) as MutableMap<*, *>
            map.clear()
        }
    }

    try {
        val jarFileFactory = Class.forName("sun.net.www.protocol.jar.JarFileFactory")

        clearMap(jarFileFactory.getDeclaredField("fileCache"))
        clearMap(jarFileFactory.getDeclaredField("urlCache"))
    } catch (ignore: Exception) {
    }
}