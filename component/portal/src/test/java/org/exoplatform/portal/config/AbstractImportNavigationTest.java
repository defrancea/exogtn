/*
 * Copyright (C) 2011 eXo Platform SAS.
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

package org.exoplatform.portal.config;

import org.exoplatform.portal.mop.navigation.NodeContext;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 */
public abstract class AbstractImportNavigationTest extends AbstractImportTest
{

   @Override
   protected final String getConfig1()
   {
      return "merge1";
   }

   @Override
   protected final String getConfig2()
   {
      return "merge2";
   }

   protected abstract void assertState(NodeContext<?> root);

   @Override
   protected final void afterTwoPhasesBoot(NodeContext<?> root)
   {
      assertEquals(1, root.getNodeCount());
      assertNotNull(root.get("foo"));
   }

   @Override
   protected final void afterTwoPhaseNoOverrideReboot(NodeContext<?> root)
   {
      assertEquals(1, root.getNodeCount());
      assertNotNull(root.get("foo"));
   }

   @Override
   protected final void afterOnePhaseBoot(NodeContext<?> root)
   {
      assertState(root);
   }

   @Override
   protected final void afterTwoPhaseOverrideReboot(NodeContext<?> root)
   {
      assertState(root);
   }

   @Override
   protected final void afterTwoPhaseNoOverrideReconfigure(NodeContext<?> root)
   {
      assertState(root);
   }
}
