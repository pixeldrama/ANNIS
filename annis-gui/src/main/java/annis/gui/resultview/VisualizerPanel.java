/*
 * Copyright 2011 Corpuslinguistic working group Humboldt University Berlin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package annis.gui.resultview;

import annis.gui.Helper;
import annis.gui.PluginSystem;
import annis.gui.VisualizationToggle;
import annis.gui.media.MediaPlayer;
import annis.gui.visualizers.VisualizerInput;
import annis.gui.visualizers.VisualizerPlugin;
import annis.resolver.ResolverEntry;
import annis.visualizers.LoadableVisualizer;
import com.sun.jersey.api.client.WebResource;
import com.vaadin.Application;
import com.vaadin.terminal.ApplicationResource;
import com.vaadin.terminal.StreamResource;
import com.vaadin.terminal.ThemeResource;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomLayout;
import com.vaadin.ui.ProgressIndicator;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.ChameleonTheme;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.SaltProject;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sCorpusStructure.SDocument;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCommon.sDocumentStructure.SToken;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SNode;
import static annis.model.AnnisConstants.*;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SDATATYPE;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SFeature;
import de.hu_berlin.german.korpling.saltnpepper.salt.saltCore.SGraph;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls the visibility of visualizer plugins and provides some control
 * methods for the media visualizers.
 *
 * @author Thomas Krause <thomas.krause@alumni.hu-berlin.de>
 * @author Benjamin Weißenfels <b.pixeldrama@gmail.com>
 *
 */
public class VisualizerPanel extends CustomLayout
  implements Button.ClickListener, VisualizationToggle
{

  private final Logger log = LoggerFactory.getLogger(VisualizerPanel.class);

  public static final ThemeResource ICON_COLLAPSE = new ThemeResource(
    "icon-collapse.gif");

  public static final ThemeResource ICON_EXPAND = new ThemeResource(
    "icon-expand.gif");

  private ApplicationResource resource = null;

  private Component vis;

  private transient SDocument result;

  private transient PluginSystem ps;

  private ResolverEntry entry;

  private Random rand = new Random();

  private transient Map<SNode, Long> markedAndCovered;

  private transient List<SToken> token;

  private Map<String, String> markersExact;

  private Map<String, String> markersCovered;

  private Button btEntry;

  private String htmlID;

  private String resultID;

  ;
  private transient VisualizerPlugin visPlugin;

  private Set<String> visibleTokenAnnos;

  private String segmentationName;

  private final String PERMANENT = "permanent";

  private final String ISVISIBLE = "visible";

  private final String HIDDEN = "hidden";

  private final String PRELOADED = "preloaded";

  private final static String htmlTemplate =
    "<div id=\":id\"><div location=\"btEntry\"></div>"
    + "<div location=\"progress\"></div>"
    + "<div location=\"iframe\"></div>"
    + "</div>";

  /**
   * This Constructor should be used for {@link ComponentVisualizerPlugin}
   * Visualizer.
   *
   */
  public VisualizerPanel(
    final ResolverEntry entry,
    SDocument result,
    List<SToken> token,
    Set<String> visibleTokenAnnos,
    Map<SNode, Long> markedAndCovered,
    @Deprecated Map<String, String> markedAndCoveredMap,
    @Deprecated Map<String, String> markedExactMap,
    String htmlID,
    String resultID,
    SingleResultPanel parent,
    String segmentationName,
    PluginSystem ps) throws IOException
  {
    super(new ByteArrayInputStream(htmlTemplate.replace(":id", htmlID).getBytes(
      "UTF-8")));

    visPlugin = ps.getVisualizer(entry.getVisType());

    this.ps = ps;
    this.entry = entry;
    this.markersExact = markedExactMap;
    this.markersCovered = markedAndCoveredMap;


    this.result = result;
    this.token = token;
    this.visibleTokenAnnos = visibleTokenAnnos;
    this.markedAndCovered = markedAndCovered;
    this.segmentationName = segmentationName;
    this.htmlID = htmlID;
    this.resultID = resultID;

    this.addStyleName(ChameleonTheme.PANEL_BORDERLESS);
    this.setWidth("100%");
  }

  @Override
  public void attach()
  {

    if (visPlugin == null && ps != null)
    {
      entry.setVisType(PluginSystem.DEFAULT_VISUALIZER);
      visPlugin = ps.getVisualizer(entry.getVisType());
    }

    if (entry != null && visPlugin != null)
    {

      if (HIDDEN.equalsIgnoreCase(entry.getVisibility()))
      {
        // build button for visualizer
        btEntry = new Button(entry.getDisplayName());
        btEntry.setIcon(ICON_EXPAND);
        btEntry.setStyleName(ChameleonTheme.BUTTON_BORDERLESS + " "
          + ChameleonTheme.BUTTON_SMALL);
        btEntry.addListener((Button.ClickListener) this);
        addComponent(btEntry, "btEntry");
      }
      else
      {

        if (ISVISIBLE.equalsIgnoreCase(entry.getVisibility())
          || PRELOADED.equalsIgnoreCase(entry.getVisibility()))
        {
          // build button for visualizer
          btEntry = new Button(entry.getDisplayName());
          btEntry.setIcon(ICON_COLLAPSE);
          btEntry.setStyleName(ChameleonTheme.BUTTON_BORDERLESS + " "
            + ChameleonTheme.BUTTON_SMALL);
          btEntry.addListener((Button.ClickListener) this);
          addComponent(btEntry, "btEntry");
        }


        // create the visualizer and calc input
        try
        {
          vis = createComponent();
          if (vis != null)
          {
            vis.setVisible(true);
            addComponent(vis, "iframe");
          }
        }
        catch (Exception ex)
        {
          getWindow().showNotification(
            "Could not create visualizer " + visPlugin.getShortName(),
            ex.toString(),
            Window.Notification.TYPE_TRAY_NOTIFICATION);
          log.error("Could not create visualizer " + visPlugin.getShortName(),
            ex);
        }


        if (PRELOADED.equalsIgnoreCase(entry.getVisibility()))
        {
          btEntry.setIcon(ICON_EXPAND);
          if (vis != null)
          {
            vis.setVisible(false);
          }
        }

      }
    } // end if entry not null

  }

  private Component createComponent()
  {
    if (visPlugin == null)
    {
      return null;
    }

    final Application application = getApplication();
    final VisualizerInput input = createInput();

    Component c = visPlugin.createComponent(input, application);
    c.setVisible(false);

    return c;
  }

  private VisualizerInput createInput()
  {
    VisualizerInput input = new VisualizerInput();
    input.setAnnisWebServiceURL(getApplication().getProperty(
      "AnnisWebService.URL"));
    input.setContextPath(Helper.getContext(getApplication()));
    input.setDotPath(getApplication().getProperty("DotPath"));

    input.setId(resultID);

    input.setMarkableExactMap(markersExact);
    input.setMarkableMap(markersCovered);
    input.setMarkedAndCovered(markedAndCovered);
    input.setVisPanel(this);

    input.setResult(result);
    input.setToken(token);
    input.setVisibleTokenAnnos(visibleTokenAnnos);
    input.setSegmentationName(segmentationName);

    if (entry != null)
    {
      input.setMappings(entry.getMappings());
      input.setNamespace(entry.getNamespace());
      String template = Helper.getContext(getApplication())
        + "/Resource/" + entry.getVisType() + "/%s";
      input.setResourcePathTemplate(template);
    }

    // getting the whole document, when plugin is using text
    if (visPlugin != null
      && visPlugin.isUsingText()
      && result != null
      && result.getSDocumentGraph().getSNodes().size() > 0)
    {
      SaltProject p = getDocument(result.getSCorpusGraph().getSRootCorpus().
        get(0).getSName(), result.getSName());
      SDocument wholeDocument = p.getSCorpusGraphs().get(0).
        getSDocuments().get(0);
      markedAndCovered = rebuildMarkedAndConvered(markedAndCovered, input.
        getDocument(), wholeDocument);
      input.setDocument(wholeDocument);
    }
    else
    {
      input.setDocument(result);
    }

    return input;
  }

  public void setVisibleTokenAnnosVisible(Set<String> annos)
  {
    this.visibleTokenAnnos = annos;
    if (visPlugin != null && vis != null)
    {
      visPlugin.setVisibleTokenAnnosVisible(vis, annos);
    }
  }

  public void setSegmentationLayer(String segmentationName,
    Map<SNode, Long> markedAndCovered)
  {
    this.segmentationName = segmentationName;
    this.markedAndCovered = markedAndCovered;

    if (visPlugin != null && vis != null)
    {
      visPlugin.setSegmentationLayer(vis, segmentationName, markedAndCovered);
    }
  }

  public ApplicationResource createResource(
    ByteArrayOutputStream byteStream,
    String mimeType)
  {

    StreamResource r;

    r = new StreamResource(new ByteArrayOutputStreamSource(byteStream),
      entry.getVisType() + "_" + rand.nextInt(Integer.MAX_VALUE),
      getApplication());
    r.setMIMEType(mimeType);

    return r;
  }

  private SaltProject getDocument(String toplevelCorpusName, String documentName)
  {
    SaltProject txt = null;
    try
    {
      toplevelCorpusName = URLEncoder.encode(toplevelCorpusName, "UTF-8");
      documentName = URLEncoder.encode(documentName, "UTF-8");
      WebResource annisResource = Helper.getAnnisWebResource(getApplication());
      txt = annisResource.path("query").path("graphs").path(toplevelCorpusName).
        path(
        documentName).get(SaltProject.class);
    }
    catch (Exception e)
    {
      log.error("General remote service exception", e);
    }
    return txt;
  }

  @Override
  public void detach()
  {
    super.detach();

    if (resource != null)
    {
      getApplication().removeResource(resource);
    }
  }

  @Override
  public void buttonClick(ClickEvent event)
  {
    toggleVisualizer(!visualizerIsVisible(), null);
  }

  @Override
  public boolean visualizerIsVisible()
  {
    if (vis == null || !vis.isVisible())
    {
      return false;
    }
    return true;
  }

  private void loadVisualizer(final LoadableVisualizer.Callback callback)
  {
    // check if it's necessary to create input
    if (visPlugin != null)
    {
      Executor exec = Executors.newSingleThreadExecutor();
      FutureTask<Component> future = new FutureTask<Component>(
        new LoadComponentTask())
      {
        @Override
        public void run()
        {
          try
          {
            super.run();
            // wait maximum 60 seconds
            vis = get(60, TimeUnit.SECONDS);
            synchronized (getApplication())
            {
              if (callback != null && vis instanceof LoadableVisualizer)
              {
                LoadableVisualizer loadableVis = (LoadableVisualizer) vis;
                if (loadableVis.isLoaded())
                {
                  // direct call callback since the visualizer is already ready
                  callback.visualizerLoaded((LoadableVisualizer) vis);
                }
                else
                {
                  loadableVis.clearCallbacks();
                  // add listener when player was fully loaded
                  loadableVis.addOnLoadCallBack(callback);
                }
              }

              removeComponent("progress");

              if (vis != null)
              {
                vis.setVisible(true);
                if (getComponent("iframe") == null)
                {
                  addComponent(vis, "iframe");
                }
              }
            }
          }
          catch (InterruptedException ex)
          {
            log.error("Visualizer creation interrupted " + visPlugin.
              getShortName(), ex);
          }
          catch (ExecutionException ex)
          {
            log.error("Exception when creating visualizer " + visPlugin.
              getShortName(), ex);
          }
          catch (TimeoutException ex)
          {
            log.
              error(
              "Could create visualizer " + visPlugin.getShortName() + " in 60 seconds: Timeout",
              ex);
            synchronized (getApplication())
            {
              getWindow().showNotification(
                "Could not create visualizer " + visPlugin.getShortName(),
                ex.toString(),
                Window.Notification.TYPE_WARNING_MESSAGE);
            }
            cancel(true);
          }
        }
      };
      exec.execute(future);


      btEntry.setIcon(ICON_COLLAPSE);
      ProgressIndicator progress = new ProgressIndicator();
      progress.setIndeterminate(true);
      progress.setVisible(true);
      progress.setEnabled(true);
      progress.setPollingInterval(100);
      progress.setDescription("Loading visualizer" + visPlugin.getShortName());
      addComponent(progress, "progress");
    }
    // end if create input was needed

  } // end loadVisualizer

  @Override
  public void toggleVisualizer(boolean visible,
    LoadableVisualizer.Callback callback)
  {
    if (visible)
    {
      loadVisualizer(callback);
    }
    else
    {
      // hide

      if (vis != null)
      {
        vis.setVisible(false);
        if (vis instanceof MediaPlayer)
        {
          removeComponent(vis);
        }
      }

      btEntry.setIcon(ICON_EXPAND);
    }

  }

  public String getHtmlID()
  {
    return htmlID;
  }

  /**
   * Rebuild the map of marked and covered matches with new object references.
   * If a visualizer uses the whole document, the {@link VisualizerInput} gets a
   * new result object, with new SNode objects, so we have to update these
   * references.
   *
   * @param markedAndCovered the original map calculated with the partial
   * document graph
   * @param document the partial document or subgraph
   * @param wholeDocument the new complete document
   * @return a new map, with updated object/node references. The salt ids of the
   * node objects remains the same.
   */
  private Map<SNode, Long> rebuildMarkedAndConvered(
    Map<SNode, Long> markedAndCovered,
    SDocument document, SDocument wholeDocument)
  {
    Map<SNode, Long> newMarkedAndCovered = new HashMap<SNode, Long>();
    SGraph wholeSGraph = wholeDocument.getSCorpusGraph();
    SNode wholeNode;

    for (SNode node : newMarkedAndCovered.keySet())
    {
      wholeNode = wholeSGraph.getSNode(node.getSId());
      newMarkedAndCovered.put(wholeNode, markedAndCovered.get(node));

      // copy the annis features, which are not set by the annis service
      copyAnnisFeature(node, wholeNode, ANNIS_NS, FEAT_MATCHEDNODE);
    }

    // copy the annis features, which are not set by the annis service
    copyAnnisFeature(document, wholeDocument, ANNIS_NS, FEAT_MATCHEDIDS);
    return newMarkedAndCovered;
  }

  /**
   * Since there is a bug in the annis-service some ANNIS Features are not set
   * when the whole document is requested, we have to copy it manually from the
   * old nodes
   *
   * @param source orignal node
   * @param target node which is missing the annis feature
   * @param featureNameSpace namespace of the feature
   * @param featureName name of the feature
   */
  private void copyAnnisFeature(SNode source, SNode target,
    String featureNameSpace, String featureName)
  {
    SFeature sfeature;
    SFeature tmp;

    if ((sfeature = source.getSFeature(featureNameSpace, featureName)) != null)
    {
      if ((tmp = target.getSFeature(
        featureNameSpace, featureName)) == null)
      {
        target.createSFeature(sfeature.getNamespace(), sfeature.getName(),
          sfeature.getSValueSTEXT());
        log.debug("copy SFeature {} value {}", sfeature.getQName(), sfeature.
          getValueString());
      }
    }
  }

  public class LoadComponentTask implements Callable<Component>
  {

    @Override
    public Component call() throws Exception
    {
      // only create component if not already created
      if (vis == null)
      {
        return createComponent();
      }
      else
      {
        return vis;
      }
    }
  }

  public static class ByteArrayOutputStreamSource implements
    StreamResource.StreamSource
  {

    private static final Logger log = LoggerFactory.
      getLogger(ByteArrayOutputStreamSource.class);

    private transient ByteArrayOutputStream byteStream;

    public ByteArrayOutputStreamSource(ByteArrayOutputStream byteStream)
    {
      this.byteStream = byteStream;
    }

    @Override
    public InputStream getStream()
    {
      if (byteStream == null)
      {
        log.error("byte stream was null");
        return null;
      }
      return new ByteArrayInputStream(byteStream.toByteArray());
    }
  }
}
