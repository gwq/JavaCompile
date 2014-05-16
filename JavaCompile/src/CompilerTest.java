import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class CompilerTest {
    public static void main(String[] args) throws Exception {
        String source = "public class Main { public static void main(String[] rgs) {System.out.println(\"Hello World!\");} }";

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        StringSourceJavaObject sourceObject = new CompilerTest.StringSourceJavaObject("Main",
                source);
        Iterable<? extends JavaFileObject> fileObjects = Arrays.asList(sourceObject);
        
        List<String> options = new ArrayList<String>(4);
        options.add("-encoding");
        options.add("UTF-8");
        //options.add("-classpath");
        //options.add("\\C:\\Program Files\\Java\\jdk1.7.0_25\\lib\\");
        //options.add(this.classpath);
        options.add("-d");
        options.add(System.getProperty("user.dir")+File.separator);
        
        CompilationTask task = compiler.getTask(null, fileManager, null, options, null, fileObjects);
        boolean result = task.call();
        if (result) {
            System.out.println("±‡“Î≥…π¶°£");

            MyClassLoader myClassLoader = new MyClassLoader();
            Class<?> clazz = myClassLoader.loadClass("Main");
            Method method = clazz.getMethod("main", new Class[] { String[].class });
            method.invoke(null, new Object[] { null });
        }
    }

    static class StringSourceJavaObject extends SimpleJavaFileObject {
        private String content = null;

        public StringSourceJavaObject(String name, String content) throws URISyntaxException {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.content = content;
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return content;
        }
    }

    static class MyClassLoader extends ClassLoader {
        public MyClassLoader() {
            myCP = System.getProperty("user.dir");
        }

        @Override
        protected Class<?> findClass(String name) {
            try {
                File clazz = new File(myCP + File.separator + name + ".class");
                fin = new FileInputStream(clazz);

                FileChannel fcin = fin.getChannel();
                ByteBuffer bb = ByteBuffer.allocate(fin.available());
                fcin.read(bb);
                bb.flip();

                return defineClass(name, bb.array(), 0, bb.array().length);

            } catch (FileNotFoundException e) {
                e.printStackTrace();

                return null;
            } catch (IOException e) {
                e.printStackTrace();

                return null;
            }
        }

        private String myCP;
		private FileInputStream fin;
    }
}