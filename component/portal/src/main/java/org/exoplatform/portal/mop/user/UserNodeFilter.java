/*
 * Copyright (C) 2010 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.portal.mop.user;

import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.mop.Visibility;
import org.exoplatform.portal.mop.navigation.NodeFilter;
import org.exoplatform.portal.mop.navigation.NodeState;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
class UserNodeFilter implements NodeFilter
{

   /** . */
   private final UserPortalImpl userPortal;

   /** . */
   private final UserNodeFilterConfig config;

   public UserNodeFilter(UserPortalImpl userPortal, UserNodeFilterConfig config)
   {
      if (userPortal == null)
      {
         throw new NullPointerException();
      }
      if (config == null)
      {
         throw new NullPointerException();
      }

      //
      this.userPortal = userPortal;
      this.config = config;
   }

   private boolean canRead(NodeState state)
   {
      String pageRef = state.getPageRef();
      if (pageRef != null)
      {
         try
         {
            Page page = userPortal.service.getPage(pageRef);
            if (page != null)
            {
               return userPortal.service.getUserACL().hasPermission(page);
            }
         }
         catch (Exception ignore)
         {
         }
      }
      return true;
   }

   private boolean canWrite(NodeState state)
   {
      String pageRef = state.getPageRef();
      if (pageRef != null)
      {
         try
         {
            Page page = userPortal.service.getPage(pageRef);
            if (page != null)
            {
               return userPortal.service.getUserACL().hasEditPermission(page);
            }
         }
         catch (Exception ignore)
         {
         }
      }
      return false;
   }

   public boolean accept(int depth, String id, String name, NodeState state)
   {
      Visibility visibility = state.getVisibility();

      // Correct null -> displayed
      if (visibility == null)
      {
         visibility = Visibility.DISPLAYED;
      }

      // If a visibility is specified then we use it
      if (config.visibility != null && !config.visibility.contains(visibility))
      {
         return false;
      }

      //
      UserACL acl = userPortal.service.getUserACL();

      // Perform authorization check
      if (config.authorizationMode == UserNodeFilterConfig.AUTH_NO_CHECK)
      {
         // Do nothing here
      }
      else
      {
         if (visibility == Visibility.SYSTEM)
         {
            if (config.authorizationMode == UserNodeFilterConfig.AUTH_READ_WRITE)
            {
               String userName = userPortal.userName;
               if (!acl.getSuperUser().equals(userName))
               {
                  return false;
               }
            }
            else
            {
               if (!canRead(state))
               {
                  return false;
               }
            }
         }
         else
         {
            if (config.authorizationMode == UserNodeFilterConfig.AUTH_READ_WRITE)
            {
               if (!canRead(state))
               {
                  return false;
               }
            }
            else
            {
               if (!canRead(state))
               {
                  return false;
               }
            }
         }
      }

      // Now make the custom checks
      switch (visibility)
      {
         case SYSTEM:
            break;
         case TEMPORAL:
            if (config.temporalCheck)
            {
               long now = System.currentTimeMillis();
               if (state.getStartPublicationTime() != -1 && now < state.getStartPublicationTime())
               {
                  return false;
               }
               if (state.getEndPublicationTime() != -1 && now > state.getEndPublicationTime())
               {
                  return false;
               }
            }
            break;
      }

      //
      return true;
   }
}
