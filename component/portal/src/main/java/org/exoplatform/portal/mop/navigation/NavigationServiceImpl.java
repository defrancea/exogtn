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

package org.exoplatform.portal.mop.navigation;

import org.exoplatform.portal.mop.Described;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.Visible;
import org.exoplatform.portal.pom.config.POMSession;
import org.exoplatform.portal.pom.config.POMSessionManager;
import org.exoplatform.portal.pom.data.MappedAttributes;
import static org.exoplatform.portal.mop.navigation.Utils.*;
import static org.exoplatform.portal.pom.config.Utils.split;

import org.exoplatform.portal.pom.data.Mapper;
import org.exoplatform.portal.tree.diff.HierarchyAdapter;
import org.gatein.common.logging.Logger;
import org.gatein.common.logging.LoggerFactory;
import org.gatein.mop.api.Attributes;
import org.gatein.mop.api.workspace.Navigation;
import org.gatein.mop.api.workspace.ObjectType;
import org.gatein.mop.api.workspace.Site;
import org.gatein.mop.api.workspace.Workspace;
import org.gatein.mop.api.workspace.link.PageLink;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
public class NavigationServiceImpl implements NavigationService
{

   /** . */
   final POMSessionManager manager;

   /** . */
   private final DataCache dataCache;

   /** . */
   final Logger log = LoggerFactory.getLogger(NavigationServiceImpl.class);

   public NavigationServiceImpl(POMSessionManager manager) throws NullPointerException
   {
      this(manager, new SimpleDataCache());
   }

   public NavigationServiceImpl(POMSessionManager manager, DataCache dataCache) throws NullPointerException
   {
      if (manager == null)
      {
         throw new NullPointerException("No null pom session manager allowed");
      }
      if (dataCache == null)
      {
         throw new NullPointerException("No null data cache allowed");
      }
      this.manager = manager;
      this.dataCache = dataCache;
   }

   public NavigationContext loadNavigation(SiteKey key)
   {
      if (key == null)
      {
         throw new NullPointerException();
      }

      //
      POMSession session = manager.getSession();
      NavigationData data = dataCache.getNavigationData(session, key);
      return data != null ? new NavigationContext(data) : null;
   }

   public void saveNavigation(NavigationContext navigation) throws NullPointerException, NavigationServiceException
   {
      if (navigation == null)
      {
         throw new NullPointerException();
      }

      //
      POMSession session = manager.getSession();
      ObjectType<Site> objectType = objectType(navigation.key.getType());
      Workspace workspace = session.getWorkspace();
      Site site = workspace.getSite(objectType, navigation.key.getName());

      //
      if (site == null)
      {
         throw new NavigationServiceException(NavigationError.NAVIGATION_NO_SITE);
      }

      //
      Navigation rootNode = site.getRootNavigation();

      //
      Navigation defaultNode = rootNode.getChild("default");
      if (defaultNode == null)
      {
         defaultNode = rootNode.addChild("default");
      }

      //
      NavigationState state = navigation.state;
      if (state != null)
      {
         Integer priority = state.getPriority();
         defaultNode.getAttributes().setValue(MappedAttributes.PRIORITY, priority);
      }

      //
      dataCache.removeNavigationData(session, navigation.key);

      // Update state
      navigation.data = dataCache.getNavigationData(session, navigation.key);
      navigation.state = null;
   }

   public boolean destroyNavigation(NavigationContext navigation) throws NullPointerException, NavigationServiceException
   {
      if (navigation == null)
      {
         throw new NullPointerException();
      }
      if (navigation.data == null)
      {
         throw new IllegalArgumentException("Already removed");
      }

      //
      POMSession session = manager.getSession();
      ObjectType<Site> objectType = objectType(navigation.key.getType());
      Workspace workspace = session.getWorkspace();
      Site site = workspace.getSite(objectType, navigation.key.getName());

      //
      if (site == null)
      {
         throw new NavigationServiceException(NavigationError.NAVIGATION_NO_SITE);
      }

      //
      Navigation rootNode = site.getRootNavigation();
      Navigation defaultNode = rootNode.getChild("default");

      //
      if (defaultNode != null)
      {
         dataCache.removeNavigation(navigation.key);
         defaultNode.destroy();
         navigation.data = null;
         return true;
      }
      else
      {
         return false;
      }
   }

   public <N> NodeContext<N> loadNode(NodeModel<N> model, NavigationContext navigation, Scope scope, NodeChangeListener<NodeContext<N>> listener)
   {
      if (model == null)
      {
         throw new NullPointerException();
      }
      if (navigation == null)
      {
         throw new NullPointerException();
      }
      if (scope == null)
      {
         throw new NullPointerException();
      }
      String nodeId = navigation.data.rootId;
      if (navigation.data.rootId != null)
      {
         POMSession session = manager.getSession();
         NodeData data = dataCache.getNodeData(session, nodeId);
         if (data != null)
         {
            NodeContext<N> context = new NodeContext<N>(model, data);
            Scope.Visitor visitor = scope.get();
            expand(session, context, visitor, 0, listener);
            return context;
         }
         else
         {
            return null;
         }
      }
      else
      {
         return null;
      }
   }

   class SrcAdapter<N> implements HierarchyAdapter<String[], NodeContext<N>, String>
   {
      public String getHandle(NodeContext<N> node)
      {
         return node.data.id;
      }

      public String[] getChildren(NodeContext<N> node)
      {
         if (node.isExpanded())
         {
            ArrayList<String> array = new ArrayList<String>();
            for (NodeContext<N> current = node.getFirst();current != null;current = current.getNext())
            {
               array.add(current.data.id);
            }
            return array.toArray(new String[array.size()]);
         }
         else
         {
            return Utils.EMPTY_STRING_ARRAY;
         }
      }

      public NodeContext<N> getDescendant(NodeContext<N> node, String handle)
      {
         return node.getDescendant(handle);
      }
   }

   class DstAdapter implements HierarchyAdapter<String[], NodeData, String>
   {

      /** . */
      private final POMSession session;

      DstAdapter(POMSession session)
      {
         this.session = session;
      }

      public String getHandle(NodeData node)
      {
         return node.id;
      }

      public String[] getChildren(NodeData node)
      {
         return node.children;
      }

      public NodeData getDescendant(NodeData node, String handle)
      {
         NodeData data = dataCache.getNodeData(session, handle);
         NodeData current = data;
         while (current != null)
         {
            if (node.id.equals(current.id))
            {
               return data;
            }
            else
            {
               if (current.parentId != null)
               {
                  current = dataCache.getNodeData(session, current.parentId);
               }
               else
               {
                  current = null;
               }
            }
         }
         return null;
      }
   }

   public <N> void updateNode(NodeContext<N> root, Scope scope, NodeChangeListener<NodeContext<N>> listener) throws NullPointerException, IllegalArgumentException, NavigationServiceException
   {

      final POMSession session = manager.getSession();
      TreeContext<N> tree = root.tree;

      //
      if (tree.hasChanges())
      {
         throw new IllegalArgumentException("For now we don't accept to update a context that has pending changes");
      }

      //
      Scope.Visitor visitor;
      if (scope != null)
      {
         visitor = new FederatedVisitor<N>(root.tree, root, scope);
      }
      else
      {
         visitor = root.tree;
      }

      //
      if (root.tree.root != root)
      {
         root = root.tree.root;
      }

      //
      NodeData data = dataCache.getNodeData(session, root.data.id);
      if (data == null)
      {
         throw new NavigationServiceException(NavigationError.UPDATE_CONCURRENTLY_REMOVED_NODE);
      }

      // Switch to edit mode
      tree.editMode = true;

      // Apply diff changes to the model
      try
      {

         Update2.perform(
            root,
            new SrcAdapter<N>(),
            data,
            new DstAdapter(session),
            Update.Adapter.NODE_DATA,
            listener,
            visitor);
      }
      finally
      {
         // Disable edit mode
         tree.editMode = false;
      }

      // Now expand
      // expand(session, root, visitor, 0, listener);
   }

   private <N> void expand(
      POMSession session,
      NodeContext<N> context,
      Scope.Visitor visitor,
      int depth,
      NodeChangeListener<NodeContext<N>> listener)
   {
      if (visitor != null)
      {
         // Obtain most actual data
         NodeData cachedData = dataCache.getNodeData(session, context.data.id);

         //
         VisitMode visitMode = visitor.enter(depth, cachedData.id, cachedData.name, cachedData.state);

         //
         if (context.isExpanded())
         {
            for (NodeContext<N> current = context.getFirst();current != null;current = current.getNext())
            {
               expand(session, current, visitor, depth + 1, listener);
            }
         }
         else
         {
            if (visitMode == VisitMode.ALL_CHILDREN)
            {
               context.expand();

               NodeContext<N> previous = null;

               for (String childId : cachedData.children)
               {
                  NodeData childData = dataCache.getNodeData(session, childId);
                  if (childData != null)
                  {
                     NodeContext<N> childContext = context.insertLast(childData);

                     // Generate event
                     if (listener != null)
                     {
                        listener.onAdd(context, previous, childContext);
                        previous = childContext;
                     }

                     //
                     expand(session, childContext, visitor, depth + 1, listener);
                  }
                  else
                  {
                     throw new UnsupportedOperationException("Handle me gracefully");
                  }
               }

               //
               context.data = cachedData;
            }
         }

         //
         visitor.leave(depth, cachedData.id, cachedData.name, cachedData.state);
      }
   }

   public <N> void saveNode(NodeContext<N> context) throws NullPointerException, NavigationServiceException
   {
      final POMSession session = manager.getSession();
      TreeContext<N> tree = context.tree;
      List<NodeChange<NodeContext<N>>> changes = tree.popChanges();

      //
      final AtomicReference<NodeContext<N>> node = new AtomicReference<NodeContext<N>>();
      final Set<String> ids = new HashSet<String>();

      //
      NodeChangeListener<Navigation> persister = new NodeChangeListener.Base<Navigation>()
      {
         @Override
         public void onCreate(Navigation source, Navigation parent, Navigation previous, String name) throws NavigationServiceException
         {
            ids.add(parent.getObjectId());
            source = parent.addChild(name);
            List<Navigation> children = parent.getChildren();
            int index = previous != null ? children.indexOf(previous) + 1 : 0;
            children.add(index, source);
            node.get().data = new NodeData(source);
            node.get().handle = node.get().data.id;
         }

         @Override
         public void onDestroy(Navigation source, Navigation parent)
         {
            ids.add(source.getObjectId());
            ids.add(parent.getObjectId());
            source.destroy();
         }

         @Override
         public void onRename(Navigation source, Navigation parent, String name) throws NavigationServiceException
         {
            ids.add(source.getObjectId());
            ids.add(parent.getObjectId());
            List<Navigation> children = parent.getChildren();
            int index = children.indexOf(source);
            source.setName(name);
            children.add(index, source);
         }

         @Override
         public void onUpdate(Navigation source, NodeState state) throws NavigationServiceException
         {
            ids.add(source.getObjectId());
            Workspace workspace = source.getSite().getWorkspace();
            String reference = state.getPageRef();
            if (reference != null)
            {
               String[] pageChunks = split("::", reference);
               ObjectType<? extends Site> siteType = Mapper.parseSiteType(pageChunks[0]);
               Site site = workspace.getSite(siteType, pageChunks[1]);
               org.gatein.mop.api.workspace.Page target = site.getRootPage().getChild("pages").getChild(pageChunks[2]);
               PageLink link = source.linkTo(ObjectType.PAGE_LINK);
               link.setPage(target);
            }
            else
            {
               PageLink link = source.linkTo(ObjectType.PAGE_LINK);
               link.setPage(null);
            }

            //
            Described described = source.adapt(Described.class);
            described.setName(state.getLabel());

            //
            Visible visible = source.adapt(Visible.class);
            visible.setVisibility(state.getVisibility());

            //
            visible.setStartPublicationDate(state.getStartPublicationDate());
            visible.setEndPublicationDate(state.getEndPublicationDate());

            //
            Attributes attrs = source.getAttributes();
            attrs.setValue(MappedAttributes.URI, state.getURI());
            attrs.setValue(MappedAttributes.ICON, state.getIcon());
         }

         @Override
         public void onMove(Navigation source, Navigation from, Navigation to, Navigation previous) throws NavigationServiceException
         {
            ids.add(source.getObjectId());
            ids.add(from.getObjectId());
            ids.add(to.getObjectId());
            List<Navigation> children = to.getChildren();
            int index = previous != null ? children.indexOf(previous) + 1 : 0;
            children.add(index, source);
         }
      };

      //
      NodeChangeListener<NodeContext<N>> merger = new NodeChangeMerger<N, POMSession, Navigation>(
         session,
         HierarchyManager.MOP,
         persister
      );

      // Compute set of ids to invalidate
      for (final NodeChange<NodeContext<N>> src : changes)
      {
         node.set(src.source);
         src.dispatch(merger);
      }

      // Make consistent
      update(context);

      //
      dataCache.removeNodeData(session, ids);
   }

   private <N> void update(NodeContext<N> context) throws NavigationServiceException
   {
      context.data = context.toData();
      context.state = null;
      if (context.isExpanded())
      {
         for (NodeContext<N> child : context.getContexts())
         {
            update(child);
         }
      }
   }

   public <N> void rebaseNode(NodeContext<N> root, Scope scope, NodeChangeListener<NodeContext<N>> listener) throws NavigationServiceException
   {
      // No changes -> do an update operation instead as it's simpler and cheaper
      if (!root.tree.hasChanges())
      {
         updateNode(root, scope, listener);
      }

      //
      Scope.Visitor visitor;
      if (scope != null)
      {
         visitor = new FederatedVisitor<N>(root.tree.origin(), root, scope);
      }
      else
      {
         visitor = root.tree.origin();
      }

      //
      if (root.tree.root != root)
      {
         root = root.tree.root;
      }

      //
      POMSession session = manager.getSession();
      NodeData data = dataCache.getNodeData(session, root.getId());
      final NodeContext<N> context = new NodeContext<N>(root.tree.model, data);

      // Expand
      expand(session, context, visitor, 0, null);

      //
      List<NodeChange<NodeContext<N>>> changes = root.tree.peekChanges();
      NodeContext<Object> baba = (NodeContext<Object>)context;

      //
      NodeChangeListener<NodeContext<N>> persister = new NodeChangeListener.Base<NodeContext<N>>()
      {
         @Override
         public void onCreate(NodeContext<N> source, NodeContext<N> parent, NodeContext<N> previous, String name) throws NavigationServiceException
         {
            source = new NodeContext<N>(context.tree, name, new NodeState.Builder().capture());
            source.expand();
            context.tree.addChange(new NodeChange.Created<NodeContext<N>>(parent, previous, source, name));
         }

         @Override
         public void onDestroy(NodeContext<N> source, NodeContext<N> parent)
         {
            context.tree.addChange(new NodeChange.Destroyed<NodeContext<N>>(parent, source));
         }

         @Override
         public void onRename(NodeContext<N> source, NodeContext<N> parent, String name) throws NavigationServiceException
         {
            context.tree.addChange(new NodeChange.Renamed<NodeContext<N>>(parent, source, name));
         }

         @Override
         public void onUpdate(NodeContext<N> source, NodeState state) throws NavigationServiceException
         {
            context.tree.addChange(new NodeChange.Updated<NodeContext<N>>(source, state));
         }

         @Override
         public void onMove(NodeContext<N> source, NodeContext<N> from, NodeContext<N> to, NodeContext<N> previous) throws NavigationServiceException
         {
            context.tree.addChange(new NodeChange.Moved<NodeContext<N>>(from, to, previous, source));
         }
      };

      //
      NodeChangeListener<NodeContext<N>> merger = new NodeChangeMerger<N, Object, NodeContext<N>>(
         baba.tree,
         (HierarchyManager) HierarchyManager.CONTEXT,
         persister
      );

      //
      for (NodeChange<NodeContext<N>> change : changes)
      {
         change.dispatch(merger);
      }

      //
      HierarchyAdapter<String[], NodeContext<N>, String> aaa = new HierarchyAdapter<String[], NodeContext<N>, String>()
      {
         public String getHandle(NodeContext<N> node)
         {
            return node.handle;
         }

         public String[] getChildren(NodeContext<N> node)
         {
            ArrayList<String> blah = new ArrayList<String>();
            for (NodeContext<N> current = node.getFirst(); current != null; current = current.getNext())
            {
               blah.add(current.handle);
            }
            return blah.toArray(new String[blah.size()]);
         }

         public NodeContext<N> getDescendant(NodeContext<N> node, String handle)
         {
            return node.getDescendant(handle);
         }
      };

      //
      Update2.perform(
         root,
         aaa,
         context,
         aaa,
         new Update.Adapter<NodeContext<N>>()
         {
            public NodeData getData(NodeContext<N> node)
            {
               return node.data;
            }
         },
         listener,
         context.tree);
   }

   public void clearCache()
   {
      dataCache.clear();
   }

   private static class FederatedVisitor<N> implements Scope.Visitor
   {

      /** . */
      private final Scope.Visitor visitor;

      /** . */
      private final NodeContext<N> federationRoot;

      /** . */
      private final int federationDepth;

      /** . */
      private final Scope federatedScope;

      /** . */
      private Scope.Visitor federated;

      private FederatedVisitor(Scope.Visitor visitor, NodeContext<N> federationRoot, Scope federatedScope)
      {
         this.visitor = visitor;
         this.federationRoot = federationRoot;
         this.federatedScope = federatedScope;
         this.federated = null;
         this.federationDepth = federationRoot.getDepth(federationRoot.tree.root);
      }

      public VisitMode enter(int depth, String id, String name, NodeState state)
      {
         if (federationRoot.handle.equals(id))
         {
            federated = federatedScope.get();
         }

         //
         VisitMode visit;
         if (federated != null)
         {
            visit = federated.enter(depth - federationDepth, id, name, state);
         }
         else
         {
            visit = VisitMode.NO_CHILDREN;
         }

         // Override
         VisitMode override = visitor.enter(depth, id, name, state);
         if (override == VisitMode.ALL_CHILDREN)
         {
            visit = VisitMode.ALL_CHILDREN;
         }

         //
         return visit;
      }

      public void leave(int depth, String id, String name, NodeState state)
      {
         if (federationRoot.handle.equals(id))
         {
            federated = null;
         }

         //
         if (federated != null)
         {
            federated.leave(depth - federationDepth, id, name, state);
         }
      }
   }
}
