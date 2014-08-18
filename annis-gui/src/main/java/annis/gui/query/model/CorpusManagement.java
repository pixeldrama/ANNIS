/*
 * Copyright 2014 Corpuslinguistic working group Humboldt University Berlin.
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
package annis.gui.query.model;

import annis.gui.CriticalServiceQueryException;
import annis.gui.ServiceQueryException;
import annis.gui.admin.model.UserManagement;
import annis.libgui.Helper;
import annis.service.objects.AnnisCorpus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.vaadin.ui.Notification;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A model that handles the corpus list
 *
 * @author Thomas Krause <krauseto@hu-berlin.de>
 */
public class CorpusManagement
{

  private final Map<String, AnnisCorpus> corpora = new TreeMap<>();

  private WebResource rootResource;

  private final Logger log = LoggerFactory.getLogger(CorpusManagement.class);

  public void fetchFromService() throws CriticalServiceQueryException, ServiceQueryException
  {
    if (rootResource != null)
    {
      corpora.clear();

      try
      {
        WebResource rootRes = Helper.getAnnisWebResource();
        List<AnnisCorpus> corporaList = rootRes.path("query").path("corpora")
          .get(new GenericType<List<AnnisCorpus>>()
            {
          });

        for (AnnisCorpus c : corporaList)
        {
          corpora.put(c.getName(), c);
        }
      }
      catch (ClientHandlerException ex)
      {
        log.error(null, ex);
        throw new ServiceQueryException("Service not available: " + ex.
          getLocalizedMessage());
      }
      catch (UniformInterfaceException ex)
      {
        if (ex.getResponse().getStatus() == Response.Status.UNAUTHORIZED.
          getStatusCode())
        {
          throw new CriticalServiceQueryException(
            "You are not authorized to get the corpus list.");
        }
        else
        {
          log.error(null, ex);
          throw new ServiceQueryException("Remote exception: " + ex.
            getLocalizedMessage());
        }
      }

    }
  }

  public ImmutableList<AnnisCorpus> getCorpora()
  {
    return ImmutableList.copyOf(corpora.values());
  }
  
  public ImmutableSet<String> getCorpusNames()
  {
    return ImmutableSet.copyOf(corpora.keySet());
  }

  public WebResource getRootResource()
  {
    return rootResource;
  }

  public void setRootResource(WebResource rootResource)
  {
    this.rootResource = rootResource;
  }

}
