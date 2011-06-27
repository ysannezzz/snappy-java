/*--------------------------------------------------------------------------
 *  Copyright 2011 Taro L. Saito
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/
//--------------------------------------
// XerialJ
//
// SnappyLoaderTest.java
// Since: 2011/06/22 23:59:47
//
// $URL$
// $Author$
//--------------------------------------
package org.xerial.snappy;

import static org.junit.Assert.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewMethod;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.junit.Ignore;
import org.junit.Test;
import org.xerial.util.FileResource;
import org.xerial.util.log.Logger;

public class SnappyLoaderTest
{
    private static Logger _logger = Logger.getLogger(SnappyLoaderTest.class);

    public static BufferedInputStream openByteStream(Class< ? > referenceClass, String resourceFileName)
            throws IOException {
        URL url = FileResource.find(referenceClass, resourceFileName);
        if (url != null) {
            return new BufferedInputStream(url.openStream());
        }
        else
            return null;
    }

    public static <T> String loadIntoString(Class<T> referenceClass, String path) throws IOException {
        BufferedInputStream in = openByteStream(referenceClass, path);
        if (in == null)
            throw new FileNotFoundException(
                    String.format("reference class:%s, path:%s", referenceClass.getName(), path));

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            byte[] tmp = new byte[4028];
            for (int readBytes = 0; (readBytes = in.read(tmp)) != -1;) {
                buf.write(tmp, 0, readBytes);
            }
            buf.flush();
            return buf.toString();
        }
        finally {
            in.close();
        }
    }

    @Test
    public void loadSnappyByDiffentClassloadersInTheSameJVM() throws Exception {

        // Parent class loader cannot see Snappy.class
        ClassLoader parent = this.getClass().getClassLoader().getParent();
        ClassWorld cw = new ClassWorld();
        ClassRealm P = cw.newRealm("P", parent);

        // Prepare the child class loaders which can load Snappy.class
        URL classPath = new File("target/classes").toURI().toURL();
        ClassRealm L1 = cw.newRealm("l1", URLClassLoader.newInstance(new URL[] { classPath }, parent));
        ClassRealm L2 = cw.newRealm("l2", URLClassLoader.newInstance(new URL[] { classPath }, parent));

        // Actually load Snappy.class in a class loader
        Class< ? > S1 = L1.loadClass("org.xerial.snappy.Snappy");
        Method m = S1.getMethod("getNativeLibraryVersion");
        String v = (String) m.invoke(null);

        // Load Snappy.class from another class loader
        Class< ? > S2 = L2.loadClass("org.xerial.snappy.Snappy");
        Method m2 = S2.getMethod("getNativeLibraryVersion");
        String v2 = (String) m2.invoke(null);

        assertEquals(v, v2);

    }

    @Ignore
    @Test
    public void createByteCodeOfSnappyLoader() throws Exception {

        ClassLoader parent = this.getClass().getClassLoader().getParent();
        ClassWorld cw = new ClassWorld();
        ClassRealm L1 = cw.newRealm("l1", parent);
        ClassRealm L2 = cw.newRealm("l2", parent);

        File nativeLib = SnappyLoader.findNativeLibrary();
        assertNotNull(nativeLib);

        ClassPool pool = ClassPool.getDefault();
        CtClass cl = pool.makeClass("org.xerial.snappy.SnappyNativeLoader");
        cl.addField(CtField.make("static boolean isLoaded = false;", cl));
        String m1 = loadIntoString(SnappyLoaderTest.class, "load.code");
        String m2 = loadIntoString(SnappyLoaderTest.class, "loadLibrary.code");
        cl.addMethod(CtNewMethod.make(m1, cl));
        cl.addMethod(CtNewMethod.make(m2, cl));

        ProtectionDomain systemPD = System.class.getProtectionDomain();
        byte[] bytecode = cl.toBytecode();
        FileOutputStream f = new FileOutputStream("target/SnappyNativeLoader.bytecode");
        f.write(bytecode);
        f.close();

        //Class< ? > loaderClass = cl.toClass(parent, System.class.getProtectionDomain());
        //_logger.info(cl.getName());
        //Class< ? > loaderClass = cl.toClass();

        //        Class< ? > classLoader = Class.forName("java.lang.ClassLoader");
        //        java.lang.reflect.Method defineClass = classLoader.getDeclaredMethod("defineClass", new Class[] { String.class,
        //                byte[].class, int.class, int.class, ProtectionDomain.class });
        //
        //        defineClass.setAccessible(true);
        //        defineClass.invoke(parent, cl.getName(), bytecode, 0, bytecode.length, System.class.getProtectionDomain());
        //
        //        Class< ? > forName = parent.loadClass("org.xerial.snappy.SnappyNativeLoader");

        //Class< ? > snappyClass = L1.loadClass("org.xerial.snappy.Snappy");    // not found
        //_logger.info(snappyClass.getName());
    }

    @Test
    public void load() throws Exception {
        SnappyLoader.load();
        _logger.debug(Snappy.getNativeLibraryVersion());
    }
}
