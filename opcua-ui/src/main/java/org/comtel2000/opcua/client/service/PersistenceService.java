/*******************************************************************************
 * Copyright (c) 2016 comtel2000
 *
 * Licensed under the Apache License, version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.comtel2000.opcua.client.service;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Base64;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import org.comtel2000.opcua.client.service.PersistenceService.PreferenceContext.Root;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;

/**
 * This class allows applications to bind {@link Property} to {@link Preferences} and store and
 * retrieve it from the OS-specific registry. The selected binding {@code key} is restricted to
 * {@link Preferences#MAX_KEY_LENGTH}
 * 
 * @author comtel
 *
 */
public class PersistenceService {

  private final Preferences prefs;

  public PersistenceService(Preferences p) {
    prefs = p;
  }

  /**
   * Use the user preference tree with associated {@link PersistenceService} package name
   * 
   * @see java.util.prefs.Preferences
   */
  public PersistenceService() {
    this(Preferences.userNodeForPackage(PersistenceService.class));
  }

  /**
   * Use the user preference tree with associated given class package name
   * 
   * @param c package name
   * 
   * @see java.util.prefs.Preferences
   */
  public PersistenceService(Class<?> c) {
    this(Preferences.userNodeForPackage(c));
  }

  /**
   * Use the selected preference tree with associated given class package name
   * 
   * @param c package name
   * @param pref selected preference tree
   * 
   * @see org.comtel2000.guice.module.PreferenceContext
   */
  public PersistenceService(Class<?> c, Root pref) {
    this(pref == Root.USER ? Preferences.userNodeForPackage(c)
        : Preferences.systemNodeForPackage(c));
  }

  @PreDestroy
  public void flush() {
    try {
      prefs.flush();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  public Preferences getPreferences() {
    return prefs;
  }

  /**
   * Generates a bidirectional binding between the {@link Property} and the application store value.
   * The store key is used by the property name {@link Property#getName()}
   * 
   * 
   * @param property {@link Property} to bind
   */
  public <T extends Serializable> void bind(ObjectProperty<T> property) {
    bind(property, property.getName());
  }

  /**
   * Generates a bidirectional binding between the {@link Property} and the application store value
   * identified by the {@code key} {@link String}.
   * 
   * @param property {@link Property} to bind
   * @param key unique application store key
   */
  @SuppressWarnings("unchecked")
  public <T extends Serializable> void bind(ObjectProperty<T> property, String key) {

    byte[] value = prefs.getByteArray(validateKey(key), null);
    if (value != null && value.length > 0) {
      try (ObjectInputStream stream =
          new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)))) {
        property.set((T) stream.readObject());
      } catch (Exception e) {
        prefs.remove(key);
        throw new RuntimeException(e);
      }
    }
    property.addListener(o -> {
      T v = property.getValue();
      if (v == null) {
        prefs.remove(key);
        return;
      }
      try (ByteArrayOutputStream obj = new ByteArrayOutputStream()) {
        try (ObjectOutputStream stream = new ObjectOutputStream(obj)) {
          stream.writeObject(v);
          stream.flush();
        }
        prefs.putByteArray(key, Base64.getEncoder().encode(obj.toByteArray()));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  /**
   * @see #bind(ObjectProperty)
   * 
   * @param property {@link Property} to bind
   */
  public void bind(BooleanProperty property) {
    bind(property, property.getName());
  }

  /**
   * @see #bind(ObjectProperty, String)
   * 
   * @param property {@link Property} to bind
   * @param key unique application store key
   */
  public void bind(BooleanProperty property, String key) {
    property.set(prefs.getBoolean(validateKey(key), property.getValue()));
    property.addListener(o -> prefs.putBoolean(key, property.getValue()));
  }

  /**
   * @see #bind(ObjectProperty)
   * 
   * @param property {@link Property} to bind
   */
  public void bind(IntegerProperty property) {
    bind(property, property.getName());
  }

  /**
   * @see #bind(ObjectProperty, String)
   * 
   * @param property {@link Property} to bind
   * @param key unique application store key
   */
  public void bind(IntegerProperty property, String key) {
    try {
      property.set(prefs.getInt(validateKey(key), property.get()));
    } catch (NumberFormatException e) {
      prefs.putInt(key, property.getValue());
    }
    property.addListener(o -> prefs.putInt(key, property.getValue()));
  }

  /**
   * @see #bind(ObjectProperty)
   * 
   * @param property {@link Property} to bind
   */
  public void bind(FloatProperty property) {
    bind(property, property.getName());
  }

  /**
   * @see #bind(ObjectProperty, String)
   * 
   * @param property {@link Property} to bind
   * @param key unique application store key
   */
  public void bind(FloatProperty property, String key) {
    try {
      property.set(prefs.getFloat(validateKey(key), property.get()));
    } catch (NumberFormatException e) {
      prefs.putFloat(key, property.getValue());
    }
    property.addListener(o -> prefs.putFloat(key, property.getValue()));
  }

  /**
   * @see #bind(ObjectProperty)
   * 
   * @param property {@link Property} to bind
   */
  public void bind(DoubleProperty property) {
    bind(property, property.getName());
  }

  /**
   * @see #bind(ObjectProperty, String)
   * 
   * @param property {@link Property} to bind
   * @param key unique application store key
   */
  public void bind(DoubleProperty property, String key) {
    try {
      property.set(prefs.getDouble(validateKey(key), property.get()));
    } catch (NumberFormatException e) {
      prefs.putDouble(key, property.getValue());
    }
    property.addListener(o -> prefs.putDouble(key, property.getValue()));
  }

  /**
   * @see #bind(ObjectProperty)
   * 
   * @param property {@link Property} to bind
   */
  public void bind(LongProperty property) {
    bind(property, property.getName());
  }

  /**
   * @see #bind(ObjectProperty, String)
   * 
   * @param property {@link Property} to bind
   * @param key unique application store key
   */
  public void bind(LongProperty property, String key) {
    try {
      property.set(prefs.getLong(validateKey(key), property.get()));
    } catch (NumberFormatException e) {
      prefs.putLong(key, property.getValue());
    }
    property.addListener(o -> prefs.putLong(key, property.getValue()));
  }

  /**
   * @see #bind(ObjectProperty)
   * 
   * @param property {@link Property} to bind
   */
  public void bind(StringProperty property) {
    bind(property, property.getName());
  }

  /**
   * @see #bind(ObjectProperty, String)
   * 
   * @param property {@link Property} to bind
   * @param key unique application store key
   */
  public void bind(final StringProperty property, String key) {
    String value = prefs.get(validateKey(key), null);
    if (value != null) {
      property.set(value);
    }
    property.addListener(o -> prefs.put(key, property.getValue()));
  }

  /**
   * Generates a bidirectional binding between the {@link ObservableList} and the application store
   * value identified by the {@code key} {@link String}.
   * 
   * @param observableList {@link ObservableList} to bind
   * @param key unique application store key
   */
  public void bind(final ObservableList<String> observableList, String key) {
    String value = prefs.get(validateKey(key), null);
    if (value != null && value.length() > 1) {
      for (String v : value.split("\u001e")) {
        if (v != null && v.trim().length() > 0 && !observableList.contains(v.trim())) {
          observableList.add(v.trim());
        }
      }
    }
    observableList.addListener((Change<? extends CharSequence> c) -> {
      if (c.next()) {
        String joined = c.getList().stream().collect(Collectors.joining("\u001e"));
        prefs.put(key, joined);
      }
    });
  }

  private final static String validateKey(String key) {
    if (key == null || key.length() == 0 || key.length() > Preferences.MAX_KEY_LENGTH) {
      throw new IllegalArgumentException("invalid binding key: " + String.valueOf(key));
    }
    return key;
  }

  @Documented
  @Retention(RUNTIME)
  @Target({ FIELD })
  public @interface PreferenceContext {

    /**
     * Select the preference package node tree
     * 
     * @return node tree from class package
     */
    public Class<?>tree() default PersistenceService.class;

    /**
     * Select the preference root tree
     * 
     * @return preference tree
     */
    public Root root() default Root.USER;

    /**
     * Select a preference tree
     * 
     * @see Preferences
     *
     */
    public enum Root {
      /** use the user's preference tree */
      USER, /** use the system preference tree */
      SYSTEM
    }

  }
}
