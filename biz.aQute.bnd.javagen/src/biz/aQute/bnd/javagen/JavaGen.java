package biz.aQute.bnd.javagen;

import java.io.File;
import java.util.Formatter;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import aQute.bnd.osgi.Processor;
import aQute.bnd.service.externalplugin.ExternalPlugin;
import aQute.bnd.service.generate.BuildContext;
import aQute.bnd.service.generate.Generator;
import aQute.lib.exceptions.Exceptions;
import aQute.lib.io.FileTree;
import aQute.lib.io.IO;
import aQute.lib.strings.Strings;

@ExternalPlugin(name = "javagen", objectClass = Generator.class)
public class JavaGen implements Generator<JavaGenOptions> {
	final static Pattern HEADER_P = Pattern.compile("---\r?\n(?<header>.*)\r?\n---\r?\n");
	final static Pattern PACKAGE_P = Pattern.compile("package\\s+(?<package>\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*\\s*);");
	
	@Override
	public Optional<String> generate(BuildContext context, JavaGenOptions options) throws Exception {
		File output = options.output().orElse(context.getFile("gen-src"));
		output.mkdirs();
		if (!output.isDirectory())
			return Optional.of("no output directory " + output);

		FileTree tree = new FileTree();
		try (Formatter errors = new Formatter()) {

			for (File file : tree.getFiles(context.getBase(), options._arguments())) {
				if ( file.isDirectory())
					continue;
				
				if (!file.isFile()) {
					errors.format("no such file %s", file);
					continue;
				}
				

				try (Processor p = new Processor(context)) {
					
					String source = IO.collect(file);
					Matcher matcher = HEADER_P.matcher(source);
					if ( matcher.lookingAt()) {
						String header = matcher.group("header");
						Properties pp=new Properties();
						pp.load(IO.reader(header));
						source = source.substring(matcher.end());
					}

					String[] parts = Strings.extension(file.getName());
					String path = parts[0]+".java";
					matcher = PACKAGE_P.matcher(source);
					if ( matcher.find()) {
						String pp = matcher.group("package").replace('.', '/');
						path = pp + "/" + path;
					}
					
					File out = IO.getFile(output, path);
					out.getParentFile().mkdirs();
					
					String result = p.getReplacer().process(source);
					IO.store(result, out);
				} catch( Exception e) {
					errors.format("%s failed %s",file, Exceptions.causes(e));
				}
			}
			String s = errors.toString();
			return s.isEmpty() ? Optional.empty() : Optional.of(s);
		}
	}

}
