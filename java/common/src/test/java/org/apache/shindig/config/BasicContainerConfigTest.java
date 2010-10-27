/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.shindig.config;

import static org.junit.Assert.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for BasicContainerConfig
 */
public class BasicContainerConfigTest {

  protected static final Map<String, Object> DEFAULT_CONTAINER =
      makeContainer("default", "inherited", "yes");
  protected static final Map<String, Object> MODIFIED_DEFAULT_CONTAINER =
      makeContainer("default", "inherited", "si");
  protected static final Map<String, Object> EXTRA_CONTAINER = makeContainer("extra");
  protected static final Map<String, Object> MODIFIED_EXTRA_CONTAINER =
      makeContainer("extra", "inherited", "no");

  protected ContainerConfig config;

  protected static Map<String, Object> makeContainer(String name, Object... values) {
    Builder<String, Object> newCtr = ImmutableMap.builder();
    newCtr.put("gadgets.container", ImmutableList.of(name));
    for (int i = 0; i < values.length / 2; ++i) {
      newCtr.put(values[i * 2].toString(), values[i * 2 + 1]);
    }
    return newCtr.build();
  }

  protected static Map<String, Object> makeContainer(List<String> name, Object... values) {
    Builder<String, Object> newCtr = ImmutableMap.builder();
    newCtr.put("gadgets.container", name);
    for (int i = 0; i < values.length / 2; ++i) {
      newCtr.put(values[i * 2].toString(), values[i * 2 + 1]);
    }
    return newCtr.build();
  }

  @Before
  public void setUp() throws Exception {
    config = new BasicContainerConfig();
    config.newTransaction().clearContainers().addContainer(DEFAULT_CONTAINER).commit();
  }

  @Test
  public void testGetContainers() throws Exception {
    config.newTransaction().addContainer(EXTRA_CONTAINER).commit();
    assertEquals(ImmutableSet.of("default", "extra"), config.getContainers());
  }

  @Test
  public void testGetProperties() throws Exception {
    assertEquals(ImmutableSet.of("gadgets.container", "inherited"),
        config.getProperties("default").keySet());
  }

  @Test
  public void testPropertyTypes() throws Exception {
    String container = "misc";
    config.newTransaction().addContainer(makeContainer("misc",
        "bool", Boolean.valueOf(true),
        "bool2", "true",
        "int", Integer.valueOf(1234),
        "string", "abcd",
        "list", ImmutableList.of("a"),
        "map", ImmutableMap.of("a", "b"))).commit();
    assertEquals(true, config.getBool(container, "bool"));
    assertEquals(true, config.getBool(container, "bool2"));
    assertEquals(1234, config.getInt(container, "int"));
    assertEquals("abcd", config.getString(container, "string"));
    assertEquals(ImmutableList.of("a"), config.getList(container, "list"));
    assertEquals(ImmutableMap.of("a", "b"), config.getMap(container, "map"));
  }

  @Test
  public void testInheritance() throws Exception {
    config.newTransaction().addContainer(EXTRA_CONTAINER).commit();
    assertEquals("yes", config.getString("default", "inherited"));
    assertEquals("yes", config.getString("extra", "inherited"));
    config.newTransaction().addContainer(MODIFIED_EXTRA_CONTAINER).commit();
    assertEquals("no", config.getString("extra", "inherited"));
    config.newTransaction().addContainer(MODIFIED_DEFAULT_CONTAINER).commit();
    config.newTransaction().addContainer(EXTRA_CONTAINER).commit();
    assertEquals("si", config.getString("extra", "inherited"));
    assertEquals("si", config.getString("extra", "inherited"));
  }

  @Test
  public void testContainersAreMergedRecursively() throws Exception {
    // Data taken from the documentation for BasicContainerConfig#mergeParents
    Map<String, Object> defaultContainer = makeContainer("default",
        "base", "/gadgets/foo",
        "user", "peter",
        "map", ImmutableMap.of("latitude", 42, "longitude", -8));
    Map<String, Object> newContainer = makeContainer("new",
        "user", "anne",
        "colour", "green",
        "map", ImmutableMap.of("longitude", 130));
    Map<String, Object> expectedContainer = makeContainer("new",
        "base", "/gadgets/foo",
        "user", "anne",
        "colour", "green",
        "map", ImmutableMap.of("latitude", 42, "longitude", 130));
    config.newTransaction().addContainer(defaultContainer).addContainer(newContainer).commit();
    assertEquals(expectedContainer, config.getProperties("new"));
  }

  @Test
  public void testAddNewContainer() throws Exception {
    config.newTransaction().addContainer(EXTRA_CONTAINER).commit();
    assertTrue(config.getContainers().contains("extra"));
    assertEquals("yes", config.getString("extra", "inherited"));
  }

  @Test
  public void testReplaceContainer() throws Exception {
    config.newTransaction().addContainer(EXTRA_CONTAINER).commit();

    config.newTransaction().addContainer(MODIFIED_EXTRA_CONTAINER).commit();
    assertTrue(config.getContainers().contains("extra"));
    assertEquals("no", config.getString("extra", "inherited"));
  }

  @Test
  public void testReadSameContainer() throws Exception {
    config.newTransaction().addContainer(EXTRA_CONTAINER).commit();

    config.newTransaction().addContainer(EXTRA_CONTAINER).commit();
    assertTrue(config.getContainers().contains("extra"));
    assertEquals("yes", config.getString("extra", "inherited"));
  }

  @Test
  public void testRemoveContainer() throws Exception {
    config.newTransaction().addContainer(EXTRA_CONTAINER).commit();

    config.newTransaction().removeContainer("extra").commit();
    assertFalse(config.getContainers().contains("extra"));
  }
  
  @Test
  public void testClearContainerConfig() throws Exception {
    config = new BasicContainerConfig();
    config
        .newTransaction()
        .clearContainers()
        .addContainer(DEFAULT_CONTAINER)
        .addContainer(EXTRA_CONTAINER)
        .commit();

    config
        .newTransaction()
        .clearContainers()
        .addContainer(DEFAULT_CONTAINER)
        .addContainer(makeContainer("additional"))
        .commit();

    assertFalse(config.getContainers().contains("extra"));
    assertTrue(config.getContainers().contains("additional"));
  }
  
  @Test
  public void testAliasesArePopulated() throws Exception {
    Map<String, Object> container =
        makeContainer(ImmutableList.of("original", "alias"), "property", "value");
    config.newTransaction().addContainer(container).commit();
    assertEquals(ImmutableSet.of("default", "original", "alias"), config.getContainers());
    assertEquals("value", config.getString("original", "property"));
    assertEquals("value", config.getString("alias", "property"));
  }
}