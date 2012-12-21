/*
 * Copyright 2012 SFB 632.
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
package annis.gui;

import java.io.Serializable;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Hold information about the configuration of a specific (sub-) instance of ANNIS.
 * 
 * Each physical installation (speak deployment) of ANNIS is able to have several
 * instances which behave differently. This is meant to provide a more specialized
 * presentation for different projects while still using only one ANNIS installation.
 * 
 * @author Thomas Krause <thomas.krause@alumni.hu-berlin.de>
 */
@XmlRootElement
public class InstanceConfig implements Serializable
{
  private String instanceName;
  private String instanceDisplayName;
  private String defaultQueryBuilder;
  private List<CorpusSet> corpusSets;
  private String defaultCorpusSet;

  /**
   * Get the internal short name of this instance.
   * @return 
   */
  @XmlTransient
  public String getInstanceName()
  {
    return instanceName;
  }

  /** @see #getInstanceName()  */
  public void setInstanceName(String instanceName)
  {
    this.instanceName = instanceName;
  }

  /** 
   * Get the external display name (used e.g. in the user interface) of this instance. 
   */
  @XmlElement(name="display-name")
  public String getInstanceDisplayName()
  {
    return instanceDisplayName;
  }

  /** @see #getInstanceDisplayName()  */
  public void setInstanceDisplayName(String instanceDisplayName)
  {
    this.instanceDisplayName = instanceDisplayName;
  }

  /**
   * Get the short name of the query builder that should be selected by default.
   * @return 
   */
  @XmlElement(name="default-querybuilder")
  public String getDefaultQueryBuilder()
  {
    return defaultQueryBuilder;
  }

  /** @see #getDefaultQueryBuilder()  */
  public void setDefaultQueryBuilder(String defaultQueryBuilder)
  {
    this.defaultQueryBuilder = defaultQueryBuilder;
  }

  /**
   * Get the corpus sets that are pre-defined by the instance.
   * @return 
   */
  @XmlElementWrapper(name="corpus-sets")
  public List<CorpusSet> getCorpusSets()
  {
    return corpusSets;
  }

  /** @see #getCorpusSets()  */
  public void setCorpusSets(List<CorpusSet> corpusSets)
  {
    this.corpusSets = corpusSets;
  }

  /**
   * Get the name of the corpus set that should be activated by default.
   * @return 
   */
  @XmlElement(name="default-corpusset")
  public String getDefaultCorpusSet()
  {
    return defaultCorpusSet;
  }

  /** @see #getDefaultCorpusSet()  */
  public void setDefaultCorpusSet(String defaultCorpusSet)
  {
    this.defaultCorpusSet = defaultCorpusSet;
  }
  
  
  
  
}
