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
 * ��̬���¼���Class <br>
 * Java���õ�ClassLoader�ܻ��ڼ���һ��Class֮ǰ������Class�Ƿ��Ѿ������ع� <br>
 * �Ѿ������ع���Class������صڶ��� <br>
 * ���Ҫ�����¼���Class��������Ҫʵ���Լ���ClassLoader <br>
 * ����һ�������ǣ�ÿ�������ص�Class����Ҫ������(link)�� <br>
 * ����ͨ��ִ��ClassLoader.resolve()��ʵ�ֵģ���������� final�ģ��޷���д�� <br>
 * ClassLoader.resolve()����������һ��ClassLoaderʵ��linkһ��Class���Σ� <br>
 * ��ˣ�����Ҫ���¼���һ�� Class��ʱ����Ҫ����Newһ���Լ���ClassLoaderʵ���� <br>
 * һ��Class���ܱ�һ��ClassLoaderʵ���������Σ����ǿ��Ա���ͬ��ClassLoaderʵ�����أ� <br>
 * �������µ����� <br>
 * ��һ��JavaӦ���У�Class�Ǹ�������ȫ��������+�������ͼ������� ClassLoader��Ψһ��ʶ�ģ� <br>
 * ��ͬ��ClassLoader�������ͬ�����ǲ��ܻ���ת���ġ� <br>
 * ����İ취��ʹ�ýӿڻ��߸��ֻ࣬���¼���ʵ����������༴�ɡ� <br>
 * ���Լ�ʵ�ֵ�ClassLoader�У�����Ҫ���ؽӿڻ��߸����ʱ��Ҫ�������ClassLoaderȥ���� <br>
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
     * ����Java���루������������ȷ�ԣ�
     * 
     * @param className
     * @param javaCode
     * @return ����ͨ����Ϊnull����ͨ�����ش�����־
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
            System.out.println("����ɹ�����ʱ:" + (end - start) + "ms");
            //�������ļ�������
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
            System.out.println("����ʧ��:\n" + error);
            return error.toString();
        }
        return null;
    }

    /**
     * ����Java���루�������ɿ���java����
     * 
     * @param className
     * @param javaCode
     * @return ����ͨ��������Ӧ���󣬲�ͨ����Ϊnull
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
//            logger.info("����ɹ�����ʱ:" + (end - start) + "ms");
//        } else {
//            StringBuilder error = new StringBuilder();
//            for (Object diagnostic : diagListener.getDiagnostics()) {
//                compilePrint(javaCode, error, (Diagnostic) diagnostic);
//            }
//            logger.error("����ʧ��:\n" + error);
//            return error.toString();
//        }
//
//        return result;
//    }

    /**
     * ������������־
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
     * ȡԴ���ݵ�ָ����
     * 
     * @param source Դ����
     * @param line �к�
     * @return ȷ������
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
     * ����루�ҷ��ÿո���䣩
     * 
     * @param src
     * @param width
     * @return
     */
    public String ljust(String src, int width) {
        return expand(src, width, ' ', true);
    }

    /**
     * �Ҷ��루���ÿո���䣩
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
     * �̳���JavaFileManager�Ľӿڣ�StandardJavaFileManager
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
     * StringJavaFileObject������
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
