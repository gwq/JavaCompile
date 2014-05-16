import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject.Kind;


/**
 * 动态重新加载Class <br>
 * Java内置的ClassLoader总会在加载一个Class之前检查这个Class是否已经被加载过 <br>
 * 已经被加载过的Class不会加载第二次 <br>
 * 因此要想重新加载Class，我们需要实现自己的ClassLoader <br>
 * 另外一个问题是，每个被加载的Class都需要被链接(link)， <br>
 * 这是通过执行ClassLoader.resolve()来实现的，这个方法是 final的，无法重写。 <br>
 * ClassLoader.resolve()方法不允许一个ClassLoader实例link一个Class两次， <br>
 * 因此，当需要重新加载一个 Class的时候，需要重新New一个自己的ClassLoader实例。 <br>
 * 一个Class不能被一个ClassLoader实例加载两次，但是可以被不同的ClassLoader实例加载， <br>
 * 这会带来新的问题 <br>
 * 在一个Java应用中，Class是根据它的全名（包名+类名）和加载它的 ClassLoader来唯一标识的， <br>
 * 不同的ClassLoader载入的相同的类是不能互相转换的。 <br>
 * 解决的办法是使用接口或者父类，只重新加载实现类或者子类即可。 <br>
 * 在自己实现的ClassLoader中，当需要加载接口或者父类的时候，要代理给父ClassLoader去加载 <br>
 * 
 * @author ...
 * @version 2012-11
 * @since jdk1.6.0
 */
public class DynamicEngine {

    private static DynamicEngine instance = new DynamicEngine();
    private URLClassLoader parentClassLoader;
    private String classpath;
	private static Scanner scaner;

    public static DynamicEngine getInstance() {
        return instance;
    }

    private DynamicEngine() {
        this.parentClassLoader = (URLClassLoader) getClass().getClassLoader();
        buildClassPath();
    }

    private void buildClassPath() {
        StringBuilder sb = new StringBuilder();
        for (URL url : this.parentClassLoader.getURLs()) {
            String p = url.getFile();
            sb.append(p);
            sb.append(File.pathSeparator);
        }
        this.classpath = sb.toString();
    }

    /**
     * 编译Java代码（用来检查代码正确性）
     * 
     * @param className
     * @param javaCode
     * @return 编译通过则为null，不通过返回错误日志
     * @throws URISyntaxException 
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public String javaCodeCompile(String className, String javaCode) throws URISyntaxException {
        long start = System.currentTimeMillis();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector diagListener = new DiagnosticCollector();
        JavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
       
        List<StringJavaFileObject> compileUnits = new ArrayList<StringJavaFileObject>(1);
        compileUnits.add(new StringJavaFileObject(className, javaCode));
       
        List<String> options = new ArrayList<String>(4);
        options.add("-encoding");
        options.add("UTF-8");
        //options.add("-classpath");
        //options.add("\\C:\\Program Files\\Java\\jdk1.7.0_25\\lib\\");
        //options.add(this.classpath);
        options.add("-d");
        options.add(System.getProperty("user.dir")+File.separator);
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagListener, options, null, compileUnits);
        boolean success = task.call().booleanValue();
        if (success) {
            long end = System.currentTimeMillis();
            System.out.println("编译成功，用时:" + (end - start) + "ms");
            //加载类文件并运行
            MyClassLoader classloader = new MyClassLoader();
            Class<?> clazz = classloader.findClass(className);
            try {
				Method mainMethod = clazz.getMethod("main", new Class[]{String[].class});
				mainMethod.invoke(null, new Object[]{null});
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
            
        } else {
            StringBuilder error = new StringBuilder();
            for (Object diagnostic : diagListener.getDiagnostics()) {
                compilePrint(javaCode, error, (Diagnostic) diagnostic);
            }
            System.out.println("编译失败:\n" + error);
            return error.toString();
        }
        return null;
    }

    /**
     * 编译Java代码（用来生成可用java对象）
     * 
     * @param className
     * @param javaCode
     * @return 编译通过返回相应对象，不通过则为null
     */
//    @SuppressWarnings({ "rawtypes", "unchecked" })
//    public Object javaCodeToObject(String className, String javaCode) throws Exception {
//        Object result = null;
//        long start = System.currentTimeMillis();
//        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
//        DiagnosticCollector diagListener = new DiagnosticCollector();
//        ObjectFileManager fileManager = new ObjectFileManager(compiler.getStandardFileManager(diagListener, null, null));
//        List<StringFileObject> compileUnits = new ArrayList<StringFileObject>(1);
//        compileUnits.add(new StringFileObject(className, javaCode));
//        List<String> options = new ArrayList<String>(4);
//        options.add("-encoding");
//        options.add("UTF-8");
//        options.add("-classpath");
//        options.add(this.classpath);
//        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagListener, options, null, compileUnits);
//        boolean success = task.call().booleanValue();
//        if (success) {
//            ByteFileObject fileObject = fileManager.getCachedObject();
//            DynamicClassLoader dynamicClassLoader = new DynamicClassLoader(this.parentClassLoader);
//            Class clazz = dynamicClassLoader.loadClass(className, fileObject);
//            result = clazz.newInstance();
//            long end = System.currentTimeMillis();
//            logger.info("编译成功，用时:" + (end - start) + "ms");
//        } else {
//            StringBuilder error = new StringBuilder();
//            for (Object diagnostic : diagListener.getDiagnostics()) {
//                compilePrint(javaCode, error, (Diagnostic) diagnostic);
//            }
//            logger.error("编译失败:\n" + error);
//            return error.toString();
//        }
//
//        return result;
//    }

    /**
     * 构造编译错误日志
     * 
     * @param javaCode
     * @param error
     * @param diagnostic
     */
    @SuppressWarnings("rawtypes")
    private void compilePrint(String javaCode, StringBuilder error, Diagnostic diagnostic) {
        error.append(diagnostic.getMessage(null));
        error.append('\n');
        error.append(getLine(javaCode, (int) diagnostic.getLineNumber()));
        error.append('\n');
        error.append(rjust("^", (int) diagnostic.getColumnNumber()));
        error.append('\n');
    }

    /**
     * 取源数据的指定行
     * 
     * @param source 源数据
     * @param line 行号
     * @return 确定的行
     */
    public String getLine(String source, int line) {
        char[] chars = source.toCharArray();
        int count = 1;
        int n = chars.length;
        int j = 0;
        for (int i = 0; i < n;) {
            // Find a line and append it
            while (i < n && chars[i] != '\n' && chars[i] != '\r'
                    && Character.getType(chars[i]) != Character.LINE_SEPARATOR) {
                i++;
            }
            // Skip the line break reading CRLF as one line break
            int eol = i;
            if (i < n) {
                if (chars[i] == '\r' && i + 1 < n && chars[i + 1] == '\n') {
                    i += 2;
                } else {
                    i++;
                }
            }
            if (count == line) {
                return source.substring(j, eol);
            } else {
                count++;
            }
            j = i;
        }
        if (j < n) {
            return source.substring(j, n);
        }
        return source;
    }

    /**
     * 左对齐（右方用空格填充）
     * 
     * @param src
     * @param width
     * @return
     */
    public String ljust(String src, int width) {
        return expand(src, width, ' ', true);
    }

    /**
     * 右对齐（左方用空格填充）
     * 
     * @param src
     * @param width
     * @return
     */
    public String rjust(String src, int width) {
        return expand(src, width, ' ', false);
    }

    private String expand(String src, int width, char fillchar, boolean postfix) {
        String result = src;
        if (result.length() < width) {
            char[] temp = new char[width - result.length()];
            for (int i = 0; i < temp.length; i++) {
                temp[i] = fillchar;
            }
            if (postfix) {
                result = result + new String(temp);
            } else {
                result = new String(temp) + result;
            }
        }
        return result;
    }
    
   
    static class MyClassLoader extends ClassLoader {
    	
    	private String myCP;
		private FileInputStream fin;
    	
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

    }
    
    
    public static void main(String args[]) throws URISyntaxException{
    	System.out.println("Please input your code:");
    	scaner = new Scanner(System.in);
    	String EXIT = "exit";
    	StringBuilder sb = new StringBuilder();
    	while(true){
    		String code = scaner.nextLine();
    		//System.out.println(code);
    		if (code.equals(EXIT)){
    			break;
    		}else{
    			sb.append("\r\n");
    			sb.append(code);
    		}
    	}
    	for (Entry<Object, Object> entry : System.getProperties().entrySet()){
    		System.out.println(entry.getKey()+":"+entry.getValue());
    	}
    	
    	DynamicEngine dynamicEngine = new DynamicEngine();
    	dynamicEngine.javaCodeCompile("test", "public class test {public static void main(String[] args) {System.out.println(\"this is test\");}}");
    	//dynamicEngine.javaCodeCompile("test", sb.toString());
    	System.out.println(dynamicEngine.classpath);
    }
    
    
    
    
    /**
     * 继承自JavaFileManager的接口：StandardJavaFileManager
     * @author wuqiang.gwq
     *
     */
    public interface StandardJavaFileManager extends JavaFileManager {

        /**
         * Compares two file objects and return true if they represent the
         * same canonical file, zip file entry, or entry in any file
         * system based container.
         *
         * @param a a file object
         * @param b a file object
         * @return true if the given file objects represent the same
         * canonical file or zip file entry; false otherwise
         *
         * @throws IllegalArgumentException if either of the arguments
         * were created with another file manager implementation
         */
        boolean isSameFile(FileObject a, FileObject b);

        /**
         * Gets file objects representing the given files.
         *
         * @param files a list of files
         * @return a list of file objects
         * @throws IllegalArgumentException if the list of files includes
         * a directory
         */
        Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(
            Iterable<? extends File> files);

        /**
         * Gets file objects representing the given files.
         * Convenience method equivalent to:
         *
         * <pre>
         *     getJavaFileObjectsFromFiles({@linkplain java.util.Arrays#asList Arrays.asList}(files))
         * </pre>
         *
         * @param files an array of files
         * @return a list of file objects
         * @throws IllegalArgumentException if the array of files includes
         * a directory
         * @throws NullPointerException if the given array contains null
         * elements
         */
        Iterable<? extends JavaFileObject> getJavaFileObjects(File... files);

        /**
         * Gets file objects representing the given file names.
         *
         * @param names a list of file names
         * @return a list of file objects
         * @throws IllegalArgumentException if the list of file names
         * includes a directory
         */
        Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(
            Iterable<String> names);

        /**
         * Gets file objects representing the given file names.
         * Convenience method equivalent to:
         *
         * <pre>
         *     getJavaFileObjectsFromStrings({@linkplain java.util.Arrays#asList Arrays.asList}(names))
         * </pre>
         *
         * @param names a list of file names
         * @return a list of file objects
         * @throws IllegalArgumentException if the array of file names
         * includes a directory
         * @throws NullPointerException if the given array contains null
         * elements
         */
        Iterable<? extends JavaFileObject> getJavaFileObjects(String... names);

        /**
         * Associates the given path with the given location.  Any
         * previous value will be discarded.
         *
         * @param location a location
         * @param path a list of files, if {@code null} use the default
         * path for this location
         * @see #getLocation
         * @throws IllegalArgumentException if location is an output
         * location and path does not contain exactly one element
         * @throws IOException if location is an output location and path
         * does not represent an existing directory
         */
        void setLocation(Location location, Iterable<? extends File> path)
            throws IOException;

        /**
         * Gets the path associated with the given location.
         *
         * @param location a location
         * @return a list of files or {@code null} if this location has no
         * associated path
         * @see #setLocation
         */
        Iterable<? extends File> getLocation(Location location);

    }
    
    
    
    
    
    
    
    /**
     * StringJavaFileObject工具类
     * @author wuqiang.gwq
     *
     */
    
    public class StringJavaFileObject extends SimpleJavaFileObject {

        private String src;

        StringJavaFileObject(String className, String src) throws URISyntaxException {
            super(new URI(className.substring(className.lastIndexOf('.') + 1) + ".java"), Kind.SOURCE);
            this.src = src;
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return src;
        }
    }

}
