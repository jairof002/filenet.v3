// Copyright 2007-2008 Google Inc. All Rights Reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.connector.filenet4.api.IConnection;
import com.google.enterprise.connector.filenet4.api.IDocument;
import com.google.enterprise.connector.filenet4.api.IObjectFactory;
import com.google.enterprise.connector.filenet4.api.IObjectStore;
import com.google.enterprise.connector.filenet4.api.IUserContext;
import com.google.enterprise.connector.filenet4.api.IVersionSeries;
import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.RepositoryException;

import com.filenet.api.admin.PropertyDefinitionString;
import com.filenet.api.collection.ActiveMarkingList;
import com.filenet.api.collection.PropertyDefinitionList;
import com.filenet.api.constants.ClassNames;
import com.filenet.api.constants.GuidConstants;
import com.filenet.api.security.ActiveMarking;
import com.filenet.api.security.Marking;
import com.filenet.api.security.MarkingSet;
import com.filenet.api.security.User;
import com.filenet.api.util.UserContext;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileAuthorizationHandler implements AuthorizationHandler {
  private static final Logger logger =
      Logger.getLogger(FileAuthorizationHandler.class.getName());

  private final IConnection conn;
  private final IObjectFactory objectFactory;
  private final IObjectStore objectStore;
  private final boolean checkMarkings;
  private final Permissions.Factory permissionsFactory;

  public FileAuthorizationHandler(IConnection conn,
      IObjectFactory objectFactory, IObjectStore objectStore,
      boolean checkMarkings, Permissions.Factory permissionsFactory) {
    this.conn = conn;
    this.objectFactory = objectFactory;
    this.objectStore = objectStore;
    this.checkMarkings = checkMarkings;
    this.permissionsFactory = permissionsFactory;
  }

  @Override
  public void pushSubject() {
    UserContext.get().pushSubject(conn.getSubject());
  }

  @Override
  public void popSubject() {
    UserContext.get().popSubject();
  }

  @Override
  public User getUser(AuthenticationIdentity id) {
    // Lookup FileNet user and user's groups
    IUserContext uc = conn.getUserContext();
    try {
      return uc.lookupUser(id.getUsername());
    } catch (RepositoryException e) {
      logger.log(Level.WARNING, "Failed to lookup user [" + id
          + "] in FileNet", e);
      return null;
    }
  }

  /*
   * TODO(jlacey): It looked like there was partial caching when this
   * code was introduced (on the trunk) in commit 4fab233. Can the answer
   * vary by user, or should we rather be checking as a privileged user
   * (view access to an attribute with a marking set might be constrained
   * by a modification access mask)? If it can't vary, then we should
   * call this once in the constructor, rather than call this method on
   * each call to authorizeDocids.
   */
  @Override
  public boolean hasMarkings() {
    if (checkMarkings) {
      logger.info("Connector is configured to check marking sets "
          + "for authorization");
    } else {
      logger.info("Connector is configured to not check marking sets "
          + "for authorization");
      return false;
    }

    // check for the marking sets applied over the document class
    try {
      PropertyDefinitionList propertyDefinitions =
          objectFactory.getPropertyDefinitions(objectStore,
              GuidConstants.Class_Document, null);
      Iterator<?> iter = propertyDefinitions.iterator();
      while (iter.hasNext()) {
        Object propertyDefinition = iter.next();
        // Only string properties can have a marking set, and
        // get_MarkingSet is defined directly on PropertyDefinitionString.
        if (propertyDefinition instanceof PropertyDefinitionString) {
          MarkingSet markingSet = ((PropertyDefinitionString) propertyDefinition).get_MarkingSet();
          if (markingSet != null) {
            logger.info("Document class has a property with a marking set");
            return true;
          }
        }
      }

      logger.info("Document class has no properties with a marking set");
      return false;
    } catch (Exception ecp) {
      logger.log(Level.SEVERE, "Failure checking for a marking set", ecp);
      // This was the existing behavior when an exception was thrown, to
      // use checkMarkings, and if we're here then checkMarkings is true.
      return true;
    }
  }

  @VisibleForTesting
  IDocument getReleasedVersion(String docId) throws RepositoryException {
    IVersionSeries versionSeries;
    try {
      logger.log(Level.FINE, "Getting version series for document: {0}", docId);
      versionSeries = (IVersionSeries) objectStore.getObject(ClassNames.VERSION_SERIES, URLDecoder.decode(docId, "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new RepositoryException("UTF-8 encoding not supported.", e);
    }
    return versionSeries.get_ReleasedVersion();
  }

  @Override
  public AuthorizationResponse authorizeDocid(String docId, User user,
      boolean authorizeMarkings) throws RepositoryException {
    boolean isAuthorized;
    logger.log(Level.FINE, "Authorizing document: {0} for user: {1}",
        new Object[] { docId, user.get_Name() });
    IDocument releasedVersion = getReleasedVersion(docId);
    Permissions permissions = permissionsFactory.getInstance(
        releasedVersion.get_Permissions(), releasedVersion.get_Owner());
    if (permissions.authorize(user)) {
      if (authorizeMarkings) {
        logger.log(Level.FINE,
            "Authorizing document: {0} for user: {1} for Marking sets",
            new Object[] { docId, user.get_Name() });
        ActiveMarkingList activeMarkings = releasedVersion.get_ActiveMarkings();
        if (!activeMarkings.isEmpty()) {
          logger.log(Level.FINE, "Document {0} has an active marking set",
              docId);
          isAuthorized = authorizeMarking(user, activeMarkings);
        } else {
          logger.log(Level.FINE,
              "Document {0} does not have an active marking set", docId);
          isAuthorized = true;
        }
      } else {
        isAuthorized = true;
      }
    } else {
      isAuthorized = false;
    }
    logger.log(Level.FINE,
        "User {1} is {2}authorized for document DocID {0}",
        new Object[] { docId, user.get_Name(), isAuthorized ? "" : "NOT " });
    return new AuthorizationResponse(isAuthorized, docId);
  }

  /** Authorizes the given user against the ACLs of the active markings. */
  @VisibleForTesting
  boolean authorizeMarking(User user, ActiveMarkingList activeMarkings) {
    for (Object activeMarking : activeMarkings) {
      Marking marking = ((ActiveMarking) activeMarking).get_Marking();

      logger.log(Level.FINEST,
          "Authorizing user: {0} [Marking: {1}, Constraint Mask: {2}]",
          new Object[] {user.get_Name(), marking.get_MarkingValue(),
                        marking.get_ConstraintMask()});

      Permissions perms =
          permissionsFactory.getInstance(marking.get_Permissions());
      if (!perms.authorizeMarking(user, marking.get_ConstraintMask())) {
        logger.log(Level.FINER,
            "User {0} is not authorized for Marking value: {1}",
            new Object[] {user.get_Name(), marking.get_MarkingValue()});
        return false;
      }
    }
    return true;
  }
}
