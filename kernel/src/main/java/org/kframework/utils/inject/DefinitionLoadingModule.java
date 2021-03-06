// Copyright (c) 2014-2016 K Team. All Rights Reserved.
package org.kframework.utils.inject;

import org.kframework.kil.Definition;
import org.kframework.kil.loader.Context;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.KompileOptions;
import org.kframework.krun.KRunOptions;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.options.DefinitionLoadingOptions;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;

public class DefinitionLoadingModule {

    public static Context context(
            BinaryLoader loader,
            DefinitionLoadingOptions options,
            GlobalOptions global,
            Stopwatch sw,
            KExceptionManager kem,
            FileUtil files,
            KRunOptions krunOptions) {
        Context context = loader.loadOrDie(Context.class, files.resolveKompiled("context.bin"));
        context.globalOptions = global;
        context.krunOptions = krunOptions;

        sw.printIntermediate("Loading serialized context");

        sw.printIntermediate("Initializing definition paths");
        return context;
    }

    public static Definition concreteDefinition(BinaryLoader loader, FileUtil files) {
        return loader.loadOrDie(Definition.class, files.resolveKompiled("definition-concrete.bin"));
    }

    public static Definition definition(BinaryLoader loader, FileUtil files) {
        return loader.loadOrDie(Definition.class, files.resolveKompiled("definition.bin"));
    }

    public static CompiledDefinition koreDefinition(BinaryLoader loader, FileUtil files) {
        return loader.loadOrDie(CompiledDefinition.class, files.resolveKompiled("compiled.bin"));
    }

    public static KompileOptions kompileOptions(Context context, CompiledDefinition compiledDef, FileUtil files) {
        // a hack, but it's good enough for what we need from it, which is a temporary solution
        if (files.resolveKompiled("compiled.bin").exists()) {
            KompileOptions res = compiledDef.kompileOptions;
            return res;
        } else {
            Context res = context;
            return res.kompileOptions;
        }
    }

    public static File definition(File defDir, KExceptionManager kem) {
        File directory = null;
        File[] dirs = defDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });

        for (int i = 0; i < dirs.length; i++) {
            if (dirs[i].getAbsolutePath().endsWith("-kompiled")) {
                if (directory != null) {
                    throw KEMException.criticalError("Multiple compiled definitions found in the "
                            + "current working directory: " + directory.getAbsolutePath() + " and " +
                            dirs[i].getAbsolutePath());
                } else {
                    directory = dirs[i];
                }
            }
        }

        if (directory == null) {
            throw KEMException.criticalError("Could not find a compiled definition. " +
                    "Use --directory to specify one.");
        }
        if (!directory.isDirectory()) {
            throw KEMException.criticalError("Does not exist or not a directory: " + directory.getAbsolutePath());
        }
        return directory;
    }

    public static File directory(DefinitionLoadingOptions options, File workingDir, KExceptionManager kem, Map<String, String> env) {
        File directory;
        if (options.directory == null) {
            if (env.get("KRUN_COMPILED_DEF") != null) {
                File f = new File(env.get("KRUN_COMPILED_DEF"));
                if (f.isAbsolute()) {
                    directory = f;
                } else {
                    directory = new File(workingDir, env.get("KRUN_COMPILED_DEF"));
                }
            } else {
                directory = workingDir;
            }
        } else {
            File f = new File(options.directory);
            if (f.isAbsolute()) {
                directory = f;
            } else {
                directory = new File(workingDir, options.directory);
            }
        }
        if (!directory.isDirectory()) {
            throw KEMException.criticalError("Does not exist or not a directory: " + directory.getAbsolutePath());
        }
        return directory;
    }
}
