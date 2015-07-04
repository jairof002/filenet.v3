// Copyright 2011 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.filenet4;

import com.filenet.api.collection.ActiveMarkingList;
import com.filenet.api.security.ActiveMarking;
import com.filenet.api.security.Marking;
import com.filenet.api.security.User;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Authorizes a target user against the Access Control Entries for
 * all the markings applied to the target document.
 */
public class MarkingPermissions {
  private static final Logger LOGGER =
      Logger.getLogger(MarkingPermissions.class.getName());

  private final ActiveMarkingList activeMarkings;
  private final Permissions.Factory permissionsFactory;

  public MarkingPermissions(ActiveMarkingList activeMarkings,
    Permissions.Factory permissionsFactory) {
    this.activeMarkings = activeMarkings;
    this.permissionsFactory = permissionsFactory;
  }

  /**
   * To authorize a given user against the list of marking values Access
   * Control Entries for all the permission of the target document.
   *
   * @param Username which needs to be authorized.
   * @return True or False, depending on the success or failure of
   *         authorization.
   */
  public boolean authorize(User user) {
    for (Object activeMarking : activeMarkings) {
      Marking marking = ((ActiveMarking) activeMarking).get_Marking();

      LOGGER.log(Level.FINEST,
          "Authorizing user: {0} [Marking: {1}, Constraint Mask: {2}]",
          new Object[] {user.get_Name(), marking.get_MarkingValue(),
                        marking.get_ConstraintMask()});

      Permissions perms =
          permissionsFactory.getInstance(marking.get_Permissions());
      if (!perms.authorizeMarking(user, marking.get_ConstraintMask())) {
        LOGGER.log(Level.FINER,
            "User {0} is not authorized for Marking value: {1}",
            new Object[] {user.get_Name(), marking.get_MarkingValue()});
        return false;
      }
    }
    return true;
  }
}
