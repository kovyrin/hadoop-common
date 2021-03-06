/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.fs;

import java.io.*;
import java.net.URI;
import java.util.Random;
import java.util.EnumSet;
import org.apache.hadoop.fs.FileContext;
import org.apache.hadoop.fs.Options.CreateOpts;
import org.apache.hadoop.fs.Options.Rename;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.fs.CreateFlag;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FSDataInputStream;
import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;

/**
 * Test symbolic links using FileContext.
 */
public abstract class FileContextSymlinkBaseTest {
  static final long seed = 0xDEADBEEFL;
  static final int  blockSize =  8192;
  static final int  fileSize  = 16384;
 
  protected static FileContext fc;
  
  abstract protected String getScheme();
  abstract protected String testBaseDir1();
  abstract protected String testBaseDir2();
  abstract protected URI testURI();

  protected static void createAndWriteFile(FileContext fc, Path p) 
      throws IOException {
    FSDataOutputStream out;
    out = fc.create(p, EnumSet.of(CreateFlag.CREATE),
                    CreateOpts.createParent(),
                    CreateOpts.repFac((short) 1),
                    CreateOpts.blockSize(blockSize));
    byte[] buf = new byte[fileSize];
    Random rand = new Random(seed);
    rand.nextBytes(buf);
    out.write(buf);
    out.close();
  }
  
  protected static void createAndWriteFile(Path p) throws IOException {
    createAndWriteFile(fc, p);
  }

  protected void readFile(Path p) throws IOException {
    FSDataInputStream out = fc.open(p);
    byte[] actual = new byte[fileSize];
    out.readFully(actual);
    out.close();
  }

  protected void readFile(FileContext fc, Path p) throws IOException {
    FSDataInputStream out = fc.open(p);
    byte[] actual = new byte[fileSize];
    out.readFully(actual);
    out.close();
  }
  
  protected void appendToFile(Path p) throws IOException {
    FSDataOutputStream out;
    out = fc.create(p, EnumSet.of(CreateFlag.APPEND));
    byte[] buf = new byte[fileSize];
    Random rand = new Random(seed);
    rand.nextBytes(buf);
    out.write(buf);
    out.close();
  }
  
  @Before
  public void setUp() throws Exception {
    fc.mkdir(new Path(testBaseDir1()), FileContext.DEFAULT_PERM, true);
    fc.mkdir(new Path(testBaseDir2()), FileContext.DEFAULT_PERM, true);
  }
  
  @After
  public void tearDown() throws Exception { 
    fc.delete(new Path(testBaseDir1()), true);
    fc.delete(new Path(testBaseDir2()), true);
  } 
  
  @Test
  /** The root is not a symlink */
  public void testStatRoot() throws IOException {
    assertFalse(fc.getFileLinkStatus(new Path("/")).isSymlink());    
  }
  
  @Test
  /** Test setWorkingDirectory resolves symlinks */
  public void testSetWDResolvesLinks() throws IOException {
    Path dir       = new Path(testBaseDir1());
    Path linkToDir = new Path(testBaseDir1()+"/link");
    fc.createSymlink(dir, linkToDir, false);
    fc.setWorkingDirectory(linkToDir);
    // Local file system does not resolve symlinks, others do.
    if ("file".equals(getScheme())) {
      assertEquals(linkToDir.getName(), fc.getWorkingDirectory().getName());
    } else {
      assertEquals(dir.getName(), fc.getWorkingDirectory().getName());
    }
  }
  
  @Test
  /** Test create a dangling link */
  public void testCreateDanglingLink() throws IOException {
    Path file = new Path("/noSuchFile");
    Path link = new Path(testBaseDir1()+"/link");    
    try {
      fc.createSymlink(file, link, false);
    } catch (IOException x) {
      fail("failed to create dangling symlink");
    }
    try {
      fc.getFileStatus(link);
      fail("Got file status of non-existant file");
    } catch (FileNotFoundException f) {
      // Expected
    }
    fc.delete(link, false);
  } 

  @Test
  /** Test create a link to null and empty path */
  public void testCreateLinkToNullEmpty() throws IOException {
    Path link = new Path(testBaseDir1()+"/link");
    try {
      fc.createSymlink(null, link, false);
      fail("Can't create symlink to null");
    } catch (java.lang.NullPointerException e) {
      // Expected, create* with null yields NPEs
    }
    try {
      fc.createSymlink(new Path(""), link, false);
      fail("Can't create symlink to empty string");
    } catch (java.lang.IllegalArgumentException e) {
      // Expected, Path("") is invalid
    }
  } 
    
  @Test
  /** Create a link with createParent set */
  public void testCreateLinkCanCreateParent() throws IOException {
    Path file = new Path(testBaseDir1()+"/file");
    Path link = new Path(testBaseDir2()+"/linkToFile");
    createAndWriteFile(file);
    fc.delete(new Path(testBaseDir2()), true);
    try {
      fc.createSymlink(file, link, false);
      fail("Created link without first creating parent dir");
    } catch (IOException x) {
      // Expected. Need to create testBaseDir2() first.
    }
    assertFalse(fc.exists(new Path(testBaseDir2())));
    fc.createSymlink(file, link, true);
    readFile(link);
  }

  @Test
  /** Delete a link */
  public void testDeleteLink() throws IOException {
    Path file = new Path(testBaseDir1()+"/file");
    Path link = new Path(testBaseDir1()+"/linkToFile");    
    createAndWriteFile(file);  
    fc.createSymlink(file, link, false);
    readFile(link);
    fc.delete(link, false);
    try {
      readFile(link);
      fail("Symlink should have been deleted");
    } catch (IOException x) {
      // Expected
    }
    // If we deleted the link we can put it back
    fc.createSymlink(file, link, false);    
  }
  
  @Test
  /** Ensure open resolves symlinks */
  public void testOpenResolvesLinks() throws IOException {
    Path file = new Path(testBaseDir1()+"/noSuchFile");
    Path link = new Path(testBaseDir1()+"/link");
    fc.createSymlink(file, link, false);
    try {
      fc.open(link);
      fail("link target does not exist");
    } catch (FileNotFoundException x) {
      // Expected
    }
    fc.delete(link, false);
  } 

  @Test
  /** Stat a link to a file */
  public void testStatLinkToFile() throws IOException {
    Path file  = new Path(testBaseDir1()+"/file");
    Path link  = new Path(testBaseDir1()+"/linkToFile");    
    createAndWriteFile(file);
    readFile(file);
    fc.createSymlink(file, link, false);
    assertFalse(fc.getFileStatus(link).isSymlink());
    assertFalse(fc.getFileStatus(link).isDir());
    assertTrue(fc.getFileLinkStatus(link).isSymlink());
    assertFalse(fc.getFileLinkStatus(link).isDir());
    assertTrue(fc.isFile(link));
    assertFalse(fc.isDirectory(link));
    assertEquals(file.toUri().getPath(), fc.getLinkTarget(link).toString());
  }

  @Test
  /** Stat a link to a directory */
  public void testStatLinkToDir() throws IOException {
    Path dir  = new Path(testBaseDir1());
    Path link = new Path(testBaseDir1()+"/linkToDir");
    fc.createSymlink(dir, link, false);
    assertFalse(fc.getFileStatus(link).isSymlink());
    assertTrue(fc.getFileStatus(link).isDir());
    assertTrue(fc.getFileLinkStatus(link).isSymlink());
    assertFalse(fc.getFileLinkStatus(link).isDir());
    assertFalse(fc.isFile(link));
    assertTrue(fc.isDirectory(link));
    assertEquals(dir.toUri().getPath(), fc.getLinkTarget(link).toString());
  }

  @Test
  /** lstat a non-existant file */
  public void testStatNonExistantFiles() throws IOException {
    Path fileAbs = new Path("/doesNotExist");
    try {
      fc.getFileLinkStatus(fileAbs);
      fail("Got FileStatus for non-existant file");
    } catch (FileNotFoundException f) {
      // Expected
    }
    try {
      fc.getLinkTarget(fileAbs);
      fail("Got link target for non-existant file");
    } catch (FileNotFoundException f) {
      // Expected
    }
  }

  @Test
  /** Test stat'ing a regular file and directory */
  public void testStatNonLinks() throws IOException {
    Path dir   = new Path(testBaseDir1());
    Path file  = new Path(testBaseDir1()+"/file");
    createAndWriteFile(file);
    try {
      fc.getLinkTarget(dir);
      fail("Lstat'd a non-symlink");
    } catch (IOException e) {
      // Expected.
    }
    try {
      fc.getLinkTarget(file);
      fail("Lstat'd a non-symlink");
    } catch (IOException e) {
      // Expected.
    }
  }
  
  @Test
  /** Test links that link to each other */
  public void testRecursiveLinks() throws IOException {
    Path link1 = new Path(testBaseDir1()+"/link1");
    Path link2 = new Path(testBaseDir1()+"/link2");
    fc.createSymlink(link1, link2, false);
    fc.createSymlink(link2, link1, false);
    try {
      readFile(link1);
      fail("Read recursive link");
    } catch (FileNotFoundException f) {
      // LocalFs throws sub class of IOException, since File.exists 
      // returns false for a link to link. 
    } catch (IOException x) {
      assertEquals("Possible cyclic loop while following symbolic link "+
                   link1.toString(), x.getMessage());
    }    
  }

  private void checkLink(Path linkAbs, Path expectedTarget, Path targetQual) 
      throws IOException { 
    Path dir = new Path(testBaseDir1());
    // isFile/Directory
    assertTrue(fc.isFile(linkAbs));
    assertFalse(fc.isDirectory(linkAbs));

    // Check getFileStatus 
    assertFalse(fc.getFileStatus(linkAbs).isSymlink());
    assertFalse(fc.getFileStatus(linkAbs).isDir());
    assertEquals(fileSize, fc.getFileStatus(linkAbs).getLen());

    // Check getFileLinkStatus
    assertTrue(fc.getFileLinkStatus(linkAbs).isSymlink());
    assertFalse(fc.getFileLinkStatus(linkAbs).isDir());

    // Check getSymlink always returns a qualified target, except
    // when partially qualified paths are used (see tests below).
    assertEquals(targetQual.toString(), 
        fc.getFileLinkStatus(linkAbs).getSymlink().toString());
    assertEquals(targetQual, fc.getFileLinkStatus(linkAbs).getSymlink());
    // Check that the target is qualified using the file system of the 
    // path used to access the link (if the link target was not specified 
    // fully qualified, in that case we use the link target verbatim).
    if (!"file".equals(getScheme())) {
      FileContext localFc = FileContext.getLocalFSFileContext();
      Path linkQual = new Path(testURI().toString(), linkAbs);
      assertEquals(targetQual, 
                   localFc.getFileLinkStatus(linkQual).getSymlink());
    }
    
    // Check getLinkTarget
    assertEquals(expectedTarget, fc.getLinkTarget(linkAbs));
    
    // Now read using all path types..
    fc.setWorkingDirectory(dir);    
    readFile(new Path("linkToFile"));
    readFile(linkAbs);
    // And fully qualified.. (NB: for local fs this is partially qualified)
    readFile(new Path(testURI().toString(), linkAbs));
    // And partially qualified..
    boolean failureExpected = "file".equals(getScheme()) ? false : true;
    try {
      readFile(new Path(getScheme()+"://"+testBaseDir1()+"/linkToFile"));
      assertFalse(failureExpected);
    } catch (Exception e) {
      assertTrue(failureExpected);
    }
    
    // Now read using a different file context (for HDFS at least)
    if (!"file".equals(getScheme())) {
      FileContext localFc = FileContext.getLocalFSFileContext();
      readFile(localFc, new Path(testURI().toString(), linkAbs));
    }
  }
  
  @Test
  /** Test creating a symlink using relative paths */
  public void testCreateLinkUsingRelPaths() throws IOException {
    Path fileAbs = new Path(testBaseDir1(), "file");
    Path linkAbs = new Path(testBaseDir1(), "linkToFile");
    Path schemeAuth = new Path(testURI().toString()); 
    Path fileQual = new Path(schemeAuth, testBaseDir1()+"/file");
    createAndWriteFile(fileAbs);
    
    fc.setWorkingDirectory(new Path(testBaseDir1()));
    fc.createSymlink(new Path("file"), new Path("linkToFile"), false);
    checkLink(linkAbs, new Path("file"), fileQual);
    
    // Now rename the link's parent. Because the target was specified 
    // with a relative path the link should still resolve.
    Path dir1        = new Path(testBaseDir1());
    Path dir2        = new Path(testBaseDir2());
    Path linkViaDir2 = new Path(testBaseDir2(), "linkToFile");
    Path fileViaDir2 = new Path(schemeAuth, testBaseDir2()+"/file");
    fc.rename(dir1, dir2, Rename.OVERWRITE);
    assertEquals(fileViaDir2, fc.getFileLinkStatus(linkViaDir2).getSymlink());
    readFile(linkViaDir2);
  }

  @Test
  /** Test creating a symlink using absolute paths */
  public void testCreateLinkUsingAbsPaths() throws IOException {
    Path fileAbs = new Path(testBaseDir1()+"/file");
    Path linkAbs = new Path(testBaseDir1()+"/linkToFile");
    Path schemeAuth = new Path(testURI().toString()); 
    Path fileQual = new Path(schemeAuth, testBaseDir1()+"/file");
    createAndWriteFile(fileAbs);

    fc.createSymlink(fileAbs, linkAbs, false);
    checkLink(linkAbs, fileAbs, fileQual);

    // Now rename the link's parent. The target doesn't change and
    // now no longer exists so accessing the link should fail.
    Path dir1        = new Path(testBaseDir1());
    Path dir2        = new Path(testBaseDir2());
    Path linkViaDir2 = new Path(testBaseDir2(), "linkToFile");
    fc.rename(dir1, dir2, Rename.OVERWRITE);
    assertEquals(fileQual, fc.getFileLinkStatus(linkViaDir2).getSymlink());    
    try {
      readFile(linkViaDir2);
      fail("The target should not exist");
    } catch (FileNotFoundException x) {
      // Expected
    }
  } 
  
  @Test
  /** 
   * Test creating a symlink using fully and partially qualified paths.
   * NB: For local fs this actually tests partially qualified paths,
   * as they don't support fully qualified paths.
   */
  public void testCreateLinkUsingFullyQualPaths() throws IOException {
    Path fileAbs  = new Path(testBaseDir1(), "file");
    Path linkAbs  = new Path(testBaseDir1(), "linkToFile");
    Path fileQual = new Path(testURI().toString(), fileAbs);
    Path linkQual = new Path(testURI().toString(), linkAbs);
    createAndWriteFile(fileAbs);
    
    fc.createSymlink(fileQual, linkQual, false);
    checkLink(linkAbs, 
              "file".equals(getScheme()) ? fileAbs : fileQual, 
              fileQual);
    
    // Now rename the link's parent. The target doesn't change and
    // now no longer exists so accessing the link should fail.
    Path dir1        = new Path(testBaseDir1());
    Path dir2        = new Path(testBaseDir2());
    Path linkViaDir2 = new Path(testBaseDir2(), "linkToFile");
    fc.rename(dir1, dir2, Rename.OVERWRITE);    
    assertEquals(fileQual, fc.getFileLinkStatus(linkViaDir2).getSymlink());    
    try {
      readFile(linkViaDir2);
      fail("The target should not exist");
    } catch (FileNotFoundException x) {
      // Expected
    }
  } 
    
  @Test
  /** 
   * Test creating a symlink using partially qualified paths, ie a scheme 
   * but no authority and vice versa. We just test link targets here since
   * creating using a partially qualified path is file system specific.
   */
  public void testCreateLinkUsingPartQualPath1() throws IOException {
    Path schemeAuth   = new Path(testURI().toString());
    Path fileWoHost   = new Path(getScheme()+"://"+testBaseDir1()+"/file");
    Path link         = new Path(testBaseDir1()+"/linkToFile");
    Path linkQual     = new Path(schemeAuth, testBaseDir1()+"/linkToFile");
    
    // Partially qualified paths are covered for local file systems
    // in the previous test.
    if ("file".equals(getScheme())) {
      return;
    }
    FileContext localFc = FileContext.getLocalFSFileContext();
    
    fc.createSymlink(fileWoHost, link, false);
    // Partially qualified path is stored
    assertEquals(fileWoHost, fc.getLinkTarget(linkQual));    
    // NB: We do not add an authority
    assertEquals(fileWoHost.toString(),
      fc.getFileLinkStatus(link).getSymlink().toString());
    assertEquals(fileWoHost.toString(),
      fc.getFileLinkStatus(linkQual).getSymlink().toString());
    // Ditto even from another file system
    assertEquals(fileWoHost.toString(),
      localFc.getFileLinkStatus(linkQual).getSymlink().toString());
    // Same as if we accessed a partially qualified path directly
    try { 
      readFile(link);
      fail("DFS requires URIs with schemes have an authority");
    } catch (java.lang.RuntimeException e) {
      // Expected
    }
  }

  @Test
  /** Same as above but vice versa (authority but no scheme) */
  public void testCreateLinkUsingPartQualPath2() throws IOException {
    Path link         = new Path(testBaseDir1(), "linkToFile");
    Path fileWoScheme = new Path("//"+testURI().getAuthority()+ 
                                 testBaseDir1()+"/file");
    if ("file".equals(getScheme())) {
      return;
    }
    fc.createSymlink(fileWoScheme, link, false);
    assertEquals(fileWoScheme, fc.getLinkTarget(link));
    assertEquals(fileWoScheme.toString(),
      fc.getFileLinkStatus(link).getSymlink().toString());
    try {
      readFile(link);
      fail("Accessed a file with w/o scheme");
    } catch (IOException e) {
      // Expected      
      assertEquals("No AbstractFileSystem for scheme: null", e.getMessage());
    }
  }

  @Test
  /** Lstat and readlink on a normal file and directory */
  public void testLinkStatusAndTargetWithNonLink() throws IOException {
    Path schemeAuth = new Path(testURI().toString());
    Path dir        = new Path(testBaseDir1());
    Path dirQual    = new Path(schemeAuth, dir.toString());
    Path file       = new Path(testBaseDir1(), "file");
    Path fileQual   = new Path(schemeAuth, file.toString());
    createAndWriteFile(file);
    assertEquals(fc.getFileStatus(file), fc.getFileLinkStatus(file));
    assertEquals(fc.getFileStatus(dir), fc.getFileLinkStatus(dir));
    try {
      fc.getLinkTarget(file);
      fail("Get link target on non-link should throw an IOException");
    } catch (IOException x) {
      assertEquals("Path "+fileQual+" is not a symbolic link", x.getMessage());
    }
    try {
      fc.getLinkTarget(dir);
      fail("Get link target on non-link should throw an IOException");
    } catch (IOException x) {
      assertEquals("Path "+dirQual+" is not a symbolic link", x.getMessage());
    }    
  }

  @Test
  /** Test create symlink to a directory */
  public void testCreateLinkToDirectory() throws IOException {
    Path dir1      = new Path(testBaseDir1());
    Path file      = new Path(testBaseDir1(), "file");
    Path linkToDir = new Path(testBaseDir2(), "linkToDir");
    createAndWriteFile(file);
    fc.createSymlink(dir1, linkToDir, false);
    assertFalse(fc.isFile(linkToDir));
    assertTrue(fc.isDirectory(linkToDir)); 
    assertTrue(fc.getFileStatus(linkToDir).isDir());
    assertTrue(fc.getFileLinkStatus(linkToDir).isSymlink());
  }
  
  @Test
  /** Test create and remove a file through a symlink */
  public void testCreateFileViaSymlink() throws IOException {
    Path dir         = new Path(testBaseDir1());
    Path linkToDir   = new Path(testBaseDir2(), "linkToDir");
    Path fileViaLink = new Path(linkToDir, "file");
    fc.createSymlink(dir, linkToDir, false);
    createAndWriteFile(fileViaLink);
    assertTrue(fc.isFile(fileViaLink));
    assertFalse(fc.isDirectory(fileViaLink));
    assertFalse(fc.getFileLinkStatus(fileViaLink).isSymlink());
    assertFalse(fc.getFileStatus(fileViaLink).isDir());
    readFile(fileViaLink);
    fc.delete(fileViaLink, true);
    assertFalse(fc.exists(fileViaLink));
  }
  
  @Test
  /** Test make and delete directory through a symlink */
  public void testCreateDirViaSymlink() throws IOException {
    Path dir1          = new Path(testBaseDir1());
    Path subDir        = new Path(testBaseDir1(), "subDir");
    Path linkToDir     = new Path(testBaseDir2(), "linkToDir");
    Path subDirViaLink = new Path(linkToDir, "subDir");
    fc.createSymlink(dir1, linkToDir, false);
    fc.mkdir(subDirViaLink, FileContext.DEFAULT_PERM, true);
    assertTrue(fc.getFileStatus(subDirViaLink).isDir());
    fc.delete(subDirViaLink, false);
    assertFalse(fc.exists(subDirViaLink));
    assertFalse(fc.exists(subDir));
  }

  @Test
  /** Create symlink through a symlink */
  public void testCreateLinkViaLink() throws IOException {
    Path dir1        = new Path(testBaseDir1());
    Path file        = new Path(testBaseDir1(), "file");
    Path linkToDir   = new Path(testBaseDir2(), "linkToDir");
    Path fileViaLink = new Path(linkToDir, "file");
    Path linkToFile  = new Path(linkToDir, "linkToFile");
    /*
     * /b2/linkToDir            -> /b1
     * /b2/linkToDir/linkToFile -> /b2/linkToDir/file
     */
    createAndWriteFile(file);
    fc.createSymlink(dir1, linkToDir, false);
    fc.createSymlink(fileViaLink, linkToFile, false);
    assertTrue(fc.isFile(linkToFile));
    assertTrue(fc.getFileLinkStatus(linkToFile).isSymlink());
    readFile(linkToFile);
    assertEquals(fileSize, fc.getFileStatus(linkToFile).getLen());
    assertEquals(fileViaLink, fc.getLinkTarget(linkToFile));
  }

  @Test
  /** Test create symlink to a directory */
  public void testListStatusUsingLink() throws IOException {
    Path file  = new Path(testBaseDir1(), "file");
    Path link  = new Path(testBaseDir1(), "link");
    createAndWriteFile(file);
    fc.createSymlink(new Path(testBaseDir1()), link, false);
    // The size of the result is file system dependent, Hdfs is 2 (file 
    // and link) and LocalFs is 3 (file, link, file crc).
    assertTrue(fc.listStatus(link).length == 2 ||
               fc.listStatus(link).length == 3);
  }
  
  @Test
  /** Test create symlink using the same path */
  public void testCreateLinkTwice() throws IOException {
    Path file = new Path(testBaseDir1(), "file");
    Path link = new Path(testBaseDir1(), "linkToFile");
    createAndWriteFile(file);
    fc.createSymlink(file, link, false);
    try {
      fc.createSymlink(file, link, false);
      fail("link already exists");
    } catch (IOException x) {
      // Expected
    }
  } 
  
  @Test
  /** Test access via a symlink to a symlink */
  public void testCreateLinkToLink() throws IOException {
    Path dir1        = new Path(testBaseDir1());
    Path file        = new Path(testBaseDir1(), "file");
    Path linkToDir   = new Path(testBaseDir2(), "linkToDir");
    Path linkToLink  = new Path(testBaseDir2(), "linkToLink");
    Path fileViaLink = new Path(testBaseDir2(), "linkToLink/file");
    createAndWriteFile(file);
    fc.createSymlink(dir1, linkToDir, false);
    fc.createSymlink(linkToDir, linkToLink, false);
    assertTrue(fc.isFile(fileViaLink));
    assertFalse(fc.isDirectory(fileViaLink));
    assertFalse(fc.getFileLinkStatus(fileViaLink).isSymlink());
    assertFalse(fc.getFileStatus(fileViaLink).isDir());
    readFile(fileViaLink);
  }

  @Test
  /** Can not create a file with path that refers to a symlink */
  public void testCreateFileDirExistingLink() throws IOException {
    Path file = new Path(testBaseDir1(), "file");
    Path link = new Path(testBaseDir1(), "linkToFile");
    createAndWriteFile(file);
    fc.createSymlink(file, link, false);
    try {
      createAndWriteFile(link);
      fail("link already exists");
    } catch (IOException x) {
      // Expected
    }
    try {
      fc.mkdir(link, FsPermission.getDefault(), false);
      fail("link already exists");
    } catch (IOException x) {
      // Expected
    }    
  } 

  @Test
  /** Test deleting and recreating a symlink */
  public void testUseLinkAferDeleteLink() throws IOException {
    Path file = new Path(testBaseDir1(), "file");
    Path link = new Path(testBaseDir1(), "linkToFile");
    createAndWriteFile(file);
    fc.createSymlink(file, link, false);
    fc.delete(link, false);
    try {
      readFile(link);        
      fail("link was deleted");
    } catch (IOException x) {
      // Expected
    }
    readFile(file);
    fc.createSymlink(file, link, false);
    readFile(link);    
  } 
  
  
  @Test
  /** Test create symlink to . */
  public void testCreateLinkToDot() throws IOException {
    Path dir  = new Path(testBaseDir1());
    Path file = new Path(testBaseDir1(), "file");    
    Path link = new Path(testBaseDir1(), "linkToDot");
    createAndWriteFile(file);    
    fc.setWorkingDirectory(dir);
    try {
      fc.createSymlink(new Path("."), link, false);
      fail("Created symlink to dot");
      readFile(new Path(testBaseDir1(), "linkToDot/file"));
    } catch (IOException x) {
      // Expected. Path(".") resolves to "" because URI normalizes
      // the dot away and AbstractFileSystem considers "" invalid.  
    }
  }

  @Test
  /** Test create symlink to .. */
  public void testCreateLinkToDotDot() throws IOException {
    Path file        = new Path(testBaseDir1(), "test/file");
    Path dotDot      = new Path(testBaseDir1(), "test/..");
    Path linkToDir   = new Path(testBaseDir2(), "linkToDir");
    Path fileViaLink = new Path(linkToDir,      "test/file");
    // Symlink to .. is not a problem since the .. is squashed early
    assertEquals(testBaseDir1(), dotDot.toString());
    createAndWriteFile(file);
    fc.createSymlink(dotDot, linkToDir, false);
    readFile(fileViaLink);
    assertEquals(fileSize, fc.getFileStatus(fileViaLink).getLen());    
  }

  @Test
  /** Test create symlink to ../foo */
  public void testCreateLinkToDotDotPrefix() throws IOException {
    Path file = new Path(testBaseDir1(), "file");
    Path dir  = new Path(testBaseDir1(), "test");
    Path link = new Path(testBaseDir1(), "test/link");
    createAndWriteFile(file);
    fc.mkdir(dir, FsPermission.getDefault(), false);
    fc.setWorkingDirectory(dir);
    fc.createSymlink(new Path("../file"), link, false);
    readFile(link);
    assertEquals(new Path("../file"), fc.getLinkTarget(link));
  }
  
  @Test
  /** Append data to a file specified using a symlink */
  public void testAppendFileViaSymlink() throws IOException {
    Path file = new Path(testBaseDir1(), "file");
    Path link = new Path(testBaseDir1(), "linkToFile");
    createAndWriteFile(file);
    fc.createSymlink(file, link, false);
    assertEquals(fileSize, fc.getFileStatus(link).getLen());
    appendToFile(link);
    assertEquals(fileSize*2, fc.getFileStatus(link).getLen());
  }
  
  @Test
  /** Test rename file through a symlink */
  public void testRenameFileViaSymlink() throws IOException {
    Path dir1           = new Path(testBaseDir1());
    Path file           = new Path(testBaseDir1(), "file");
    Path linkToDir      = new Path(testBaseDir2(), "linkToDir");
    Path fileViaLink    = new Path(linkToDir, "file");
    Path fileNewViaLink = new Path(linkToDir, "fileNew");
    createAndWriteFile(file);
    fc.createSymlink(dir1, linkToDir, false);
    fc.rename(fileViaLink, fileNewViaLink, Rename.OVERWRITE);
    assertFalse(fc.exists(fileViaLink));
    assertFalse(fc.exists(file));
    assertTrue(fc.exists(fileNewViaLink));
  }
  
  @Test
  /** Rename a symlink */
  public void testRenameSymlink() throws IOException {
    Path file  = new Path(testBaseDir1(), "file");
    Path link1 = new Path(testBaseDir1(), "linkToFile1");
    Path link2 = new Path(testBaseDir1(), "linkToFile2");    
    createAndWriteFile(file);
    fc.createSymlink(file, link1, false);
    fc.rename(link1, link2);
    assertTrue(fc.getFileLinkStatus(link2).isSymlink());
    assertFalse(fc.getFileStatus(link2).isDir());
    readFile(link2);
    readFile(file);
    try {
      createAndWriteFile(link2);
      fail("link was not renamed");
    } catch (IOException x) {
      // Expected
    }
  } 
    
  @Test
  /** Test renaming symlink target */
  public void testMoveLinkTarget() throws IOException {
    Path file    = new Path(testBaseDir1(), "file");
    Path fileNew = new Path(testBaseDir1(), "fileNew");
    Path link    = new Path(testBaseDir1(), "linkToFile");
    createAndWriteFile(file);
    fc.createSymlink(file, link, false);
    fc.rename(file, fileNew, Rename.OVERWRITE);
    try {
      readFile(link);        
      fail("link target was renamed");
    } catch (IOException x) {
      // Expected
    }
    fc.rename(fileNew, file, Rename.OVERWRITE);
    readFile(link);
  }
  
  @Test
  /** setTimes affects the target not the link */    
  public void testSetTimes() throws IOException {
    Path file = new Path(testBaseDir1(), "file");
    Path link = new Path(testBaseDir1(), "linkToFile");
    createAndWriteFile(file);
    fc.createSymlink(file, link, false);
    long at = fc.getFileLinkStatus(link).getAccessTime(); 
    fc.setTimes(link, 2L, 3L);
    // NB: local file systems don't implement setTimes
    if (!"file".equals(getScheme())) {
      assertEquals(at, fc.getFileLinkStatus(link).getAccessTime());
      assertEquals(3, fc.getFileStatus(file).getAccessTime());
      assertEquals(2, fc.getFileStatus(file).getModificationTime());
    }
  }
}
