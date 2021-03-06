// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.filenet4;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.enterprise.connector.filenet4.Checkpoint.JsonField;
import com.google.enterprise.connector.filenet4.EngineCollectionMocks.DocumentSetMock;
import com.google.enterprise.connector.filenet4.EngineCollectionMocks.FolderSetMock;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spiimpl.PrincipalValue;

import com.filenet.api.collection.AccessPermissionList;
import com.filenet.api.collection.DocumentSet;
import com.filenet.api.collection.FolderSet;
import com.filenet.api.constants.AccessRight;
import com.filenet.api.constants.AccessType;
import com.filenet.api.constants.PermissionSource;
import com.filenet.api.constants.SecurityPrincipalType;
import com.filenet.api.core.Folder;
import com.filenet.api.security.AccessPermission;
import com.filenet.api.util.Id;

import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SecurityFolderTraverserTest extends TraverserFactoryFixture {
  private static final Date Jan_1_1970 = new Date(72000000L);

  private static final String[][] FOLDERS = {
    {"{FFFFFFFF-0000-0000-0000-000000000001}", "2015-04-01T10:00:00.100-0700"},
    {"{FFFFFFFF-0000-0000-0000-000000000002}", "2015-04-01T11:05:10.200-0700"},
    {"{FFFFFFFF-0000-0000-0000-000000000003}", "2015-04-01T12:10:20.300-0700"},
    {"{FFFFFFFF-0000-0000-0000-000000000004}", "2015-04-01T13:15:30.400-0700"},
    {"{FFFFFFFF-0000-0000-0000-000000000005}", "2015-04-01T14:20:40.500-0700"}
  };

  private static final String DOC_ID_FORMAT = "AAAAAAAA-0000-0000-%04d-%012d";

  private static int VIEW_ACCESS_RIGHTS =
      AccessRight.READ_AS_INT | AccessRight.VIEW_CONTENT_AS_INT;
  private static final SimpleDateFormat DATE_PARSER =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  private static final FolderSet EMPTY_SET = new FolderSetMock();

  private FileConnector connector;

  @Before
  public void setUp() {
    this.connector = TestObjectFactory.newFileConnector();
  }

  @Test
  public void startTraversal_Live() throws RepositoryException {
    assumeTrue(TestConnection.isLiveConnection());

    assertNull(getDocumentList_Live(new Checkpoint()));
  }

  @Test
  public void resumeTraversal_Live()
      throws RepositoryException, ParseException {
    assumeTrue(TestConnection.isLiveConnection());

    Checkpoint checkpoint = new Checkpoint();
    checkpoint.setTimeAndUuid(Checkpoint.JsonField.LAST_FOLDER_TIME, Jan_1_1970,
        Checkpoint.JsonField.UUID_FOLDER, new Id(FOLDERS[0][0]));
    DocumentList docList = getDocumentList_Live(checkpoint);
    assertNotNull(docList);

    int count = 0;
    Date prevLastModified = Jan_1_1970;
    Document doc;
    while ((doc = docList.nextDocument()) != null) {
      assertIsFolderAcl(doc);
      Checkpoint cp = new Checkpoint(docList.checkpoint());
      Date lastModified = DATE_PARSER.parse(
          cp.getString(Checkpoint.JsonField.LAST_FOLDER_TIME));
      assertTrue(lastModified.getTime() >= prevLastModified.getTime());
      prevLastModified = lastModified;
      count++;
    }
    assertTrue(count > 1);
  }

  private DocumentList getDocumentList_Live(Checkpoint checkpoint)
      throws RepositoryException {
    FileSession session = (FileSession) connector.login();
    Traverser traverser = session.getSecurityFolderTraverser();
    traverser.setBatchHint(TestConnection.batchSize);
    return traverser.getDocumentList(checkpoint);
  }

  @Test
  public void testCheckpoint() throws Exception {
    FolderSetMock folderSet = getFolderSet(1);

    Traverser traverser = getSecurityFolderTraverser(connector, folderSet);

    DocumentList docList = traverser.getDocumentList(new Checkpoint());
    int index = 0;
    Document doc;
    while ((doc = docList.nextDocument()) != null) {
      Checkpoint checkpoint = new Checkpoint(docList.checkpoint());
      assertEquals(FOLDERS[index++][1],
          checkpoint.getString(JsonField.LAST_FOLDER_TIME));
    }
    assertEquals(folderSet.size(), index);
    verifyAll();
  }

  @Test
  public void testGetDocumentList_multipleCalls() throws Exception {
    FolderSetMock folderSet = getFolderSet(1);
    assertFalse("Expected at least one folder", folderSet.isEmpty());
    Traverser traverser = getSecurityFolderTraverser(connector, folderSet);

    DocumentList first = traverser.getDocumentList(new Checkpoint());
    DocumentList second = traverser.getDocumentList(new Checkpoint());

    consumeDocumentList(first, folderSet.size());
    consumeDocumentList(second, folderSet.size());
    verifyAll();
  }

  private void consumeDocumentList(DocumentList docList, int expectedSize)
      throws RepositoryException {
    int index = 0;
    Document doc;
    while ((doc = docList.nextDocument()) != null) {
      index++;
    }
    assertEquals(expectedSize, index);
  }

  @Test
  public void testSingleFolderAclCollection() throws Exception {
    int docCount = 5;
    Folder folder = getFolder(0, docCount, EMPTY_SET);
    FolderSet folderSet = new FolderSetMock(ImmutableList.of(folder));
    List<String> expectedDocids = getExpectedDocids(1, docCount);
    testAclCollection(folderSet, expectedDocids);
  }

  @Test
  public void testMultipleUnnestedFoldersAclCollection() throws Exception {
    int docsPerFolder = 4;
    FolderSetMock folderSet = getFolderSet(docsPerFolder);
    List<String> expectedDocids =
        getExpectedDocids(FOLDERS.length, docsPerFolder);
    testAclCollection(folderSet, expectedDocids);
  }

  @Test
  public void testRecursiveAclCollection() throws Exception {
    int docsPerFolder = 4;
    FolderSetMock folderTree = getNestedFolderSet(docsPerFolder);
    List<String> expectedDocids =
        getExpectedDocids(FOLDERS.length, docsPerFolder);
    testAclCollection(folderTree, expectedDocids);
  }

  /**
   * Creates a hierarchical folder set from FOLDERS structured like:
   *     0
   *    / \
   *   1   2
   *      / \
   *     3   4
   */
  private FolderSetMock getNestedFolderSet(int docsPerFolder)
      throws ParseException {
    assertEquals("FOLDERS is unexpected size", 5, FOLDERS.length);
    return new FolderSetMock(ImmutableList.<Folder>of(
        getFolder(0, docsPerFolder,
            new FolderSetMock(ImmutableList.<Folder>of(
                getFolder(1, docsPerFolder, EMPTY_SET),
                getFolder(2, docsPerFolder,
                   new FolderSetMock(ImmutableList.<Folder>of(
                       getFolder(3, docsPerFolder, EMPTY_SET),
                       getFolder(4, docsPerFolder, EMPTY_SET)))))))));
  }

  private FolderSetMock getFolderSet(int docsPerFolder) throws Exception {
    ImmutableList.Builder<Folder> folders = ImmutableList.builder();
    for (int i = 0; i < FOLDERS.length; i++) {
      folders.add(getFolder(i, docsPerFolder, EMPTY_SET));
    }
    return new FolderSetMock(folders.build());
  }

  private Folder getFolder(int folderNum, int numDocuments,
      FolderSet subFolders) throws ParseException {
    String id = FOLDERS[folderNum][0];
    Date lastModified = DATE_PARSER.parse(FOLDERS[folderNum][1]);
    DocumentSet docSet = getChildDocuments(folderNum, numDocuments);
    Folder folder = createMock(Folder.class);
    expect(folder.get_Id()).andReturn(new Id(id)).atLeastOnce();
    expect(folder.get_FolderName()).andReturn(id).atLeastOnce();
    expect(folder.get_DateLastModified()).andReturn(lastModified).anyTimes();
    expect(folder.get_ContainedDocuments()).andReturn(docSet).atLeastOnce();
    expect(folder.get_SubFolders()).andReturn(subFolders).atLeastOnce();
    replayAndSave(folder);
    return folder;
  }

  private DocumentSet getChildDocuments(int folderNum, int docCount) {
    // Document collides with the SPI class of the same name.
    ImmutableList.Builder<com.filenet.api.core.Document> docs =
        ImmutableList.builder();
    for (int i = 1; i <= docCount; i++) {
      Id id = new Id(String.format(DOC_ID_FORMAT, folderNum, i));
      com.filenet.api.core.Document doc =
          createMock(com.filenet.api.core.Document.class);
      expect(doc.get_Id()).andReturn(id).atLeastOnce();
      expect(doc.get_Permissions()).andReturn(createACL(createACEs()))
          .atLeastOnce();
      replayAndSave(doc);
      docs.add(doc);
    }
    return new DocumentSetMock(docs.build());
  }

  private List<String> getExpectedDocids(int numFolders, int docsPerFolder) {
    ImmutableList.Builder<String> expectedDocids = ImmutableList.builder();
    // This is how getChildDocuments() numbers the documents.
    for (int i = 0; i < numFolders; i++) {
      for (int j = 1; j <= docsPerFolder; j++) {
        expectedDocids.add(String.format("{" + DOC_ID_FORMAT + "}-FLDR", i, j));
      }
    }
    return expectedDocids.build();
  }

  private AccessPermissionList createACL(final AccessPermission[] aces) {
    AccessPermissionList acl = createMock(AccessPermissionList.class);
    expect(acl.iterator()).andReturn(Iterators.forArray(aces)).atLeastOnce();
    replayAndSave(acl);
    return acl;
  }

  private AccessPermission createACE(int accessMask, AccessType acccessType,
      PermissionSource permSrc, SecurityPrincipalType granteeType,
      String grantee) {
    AccessPermission ace = createMock(AccessPermission.class);
    expect(ace.get_AccessMask()).andReturn(accessMask);
    expect(ace.get_AccessType()).andReturn(acccessType);
    expect(ace.get_PermissionSource()).andReturn(permSrc);
    expect(ace.get_GranteeType()).andReturn(granteeType);
    expect(ace.get_GranteeName()).andReturn(grantee);
    replayAndSave(ace);
    return ace;
  }

  private AccessPermission[] createACEs() {
    AccessPermission directAllowUser = createACE(VIEW_ACCESS_RIGHTS,
        AccessType.ALLOW, PermissionSource.SOURCE_DIRECT,
        SecurityPrincipalType.USER, "Direct Allow User");
    AccessPermission directDenyUser = createACE(VIEW_ACCESS_RIGHTS,
        AccessType.DENY, PermissionSource.SOURCE_DIRECT,
        SecurityPrincipalType.USER, "Direct Deny User");

    AccessPermission parentAllowUser1 = createACE(VIEW_ACCESS_RIGHTS,
        AccessType.ALLOW, PermissionSource.SOURCE_PARENT,
        SecurityPrincipalType.USER, "Parent Allow User 1");
    AccessPermission parentAllowUser2 = createACE(VIEW_ACCESS_RIGHTS,
        AccessType.ALLOW, PermissionSource.SOURCE_PARENT,
        SecurityPrincipalType.USER, "Parent Allow User 2");
    AccessPermission parentDenyUser = createACE(VIEW_ACCESS_RIGHTS,
        AccessType.DENY, PermissionSource.SOURCE_PARENT,
        SecurityPrincipalType.USER, "Parent Deny User");

    AccessPermission parentAllowGroup1 = createACE(VIEW_ACCESS_RIGHTS,
        AccessType.ALLOW, PermissionSource.SOURCE_PARENT,
        SecurityPrincipalType.GROUP, "Parent Allow Group 1");
    AccessPermission parentAllowGroup2 = createACE(VIEW_ACCESS_RIGHTS,
        AccessType.ALLOW, PermissionSource.SOURCE_PARENT,
        SecurityPrincipalType.GROUP, "Parent Allow Group 2");
    AccessPermission parentDenyGroup = createACE(VIEW_ACCESS_RIGHTS,
        AccessType.DENY, PermissionSource.SOURCE_PARENT,
        SecurityPrincipalType.GROUP, "Parent Deny Group");

    AccessPermission templateAllowGroup = createACE(VIEW_ACCESS_RIGHTS,
        AccessType.ALLOW, PermissionSource.SOURCE_TEMPLATE,
        SecurityPrincipalType.GROUP, "Template Allow Group");

    return new AccessPermission[] {
        directAllowUser, directDenyUser,
        parentAllowUser1, parentAllowUser2, parentDenyUser,
        parentAllowGroup1, parentAllowGroup2, parentDenyGroup,
        templateAllowGroup};
  }

  private void testAclCollection(FolderSet folderSet,
      List<String> expectedDocids) throws Exception {
    Traverser traverser = getSecurityFolderTraverser(connector, folderSet);

    DocumentList docList = traverser.getDocumentList(new Checkpoint());
    assertNotNull(docList);

    ImmutableList.Builder<String> actualDocids = ImmutableList.builder();
    Document doc;
    while ((doc = docList.nextDocument()) != null) {
      testAclDocument(doc);
      actualDocids.add(
          Value.getSingleValueString(doc, SpiConstants.PROPNAME_DOCID));
    }

    assertEquals(expectedDocids, actualDocids.build());
    verifyAll();
  }

  private void testAclDocument(Document doc) throws RepositoryException {
    assertIsFolderAcl(doc);

    testACLs(doc, SpiConstants.PROPNAME_ACLUSERS, 2, "Parent Allow User");
    testACLs(doc, SpiConstants.PROPNAME_ACLDENYUSERS, 1, "Parent Deny User");
    testACLs(doc, SpiConstants.PROPNAME_ACLGROUPS, 2, "Parent Allow Group");
    testACLs(doc, SpiConstants.PROPNAME_ACLDENYGROUPS, 1, "Parent Deny Group");
  }

  private void assertIsFolderAcl(Document doc) throws RepositoryException {
    assertTrue(doc.getClass().toString(), doc instanceof AclDocument);

    String docid = Value.getSingleValueString(doc, SpiConstants.PROPNAME_DOCID);
    assertTrue(docid + " is not ended with " + AclDocument.SEC_FOLDER_POSTFIX,
        docid.endsWith(AclDocument.SEC_FOLDER_POSTFIX));

    String inheritanceType = Value.getSingleValueString(doc,
        SpiConstants.PROPNAME_ACLINHERITANCETYPE);
    assertEquals(SpiConstants.AclInheritanceType.CHILD_OVERRIDES.toString(),
        inheritanceType);

    String inheritFrom = Value.getSingleValueString(doc,
        SpiConstants.PROPNAME_ACLINHERITFROM);
    assertEquals(null, inheritFrom);

    String inheritFromDocid = Value.getSingleValueString(doc,
        SpiConstants.PROPNAME_ACLINHERITFROM_DOCID);
    assertEquals(null, inheritFromDocid);
  }

  private void testACLs(Document doc, String propName, int expectedCount,
      String expectedPrefix) throws RepositoryException {
    Property propAllowUsers = doc.findProperty(propName);
    int count = 0;
    Value val;
    while ((val = propAllowUsers.nextValue()) != null) {
      count++;
      assertTrue(val.getClass().toString(), val instanceof PrincipalValue);
      String name =
          ((PrincipalValue) val).getPrincipal().getName();
      assertTrue(name + " does not start with " + expectedPrefix,
          name.startsWith(expectedPrefix));
    }
    assertEquals(expectedCount, count);
  }
}
