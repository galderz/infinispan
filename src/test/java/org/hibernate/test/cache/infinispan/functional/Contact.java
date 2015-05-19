<<<<<<< HEAD
<<<<<<< HEAD
/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat, Inc. and/or it's affiliates or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat, Inc. and/or it's affiliates.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.test.cache.infinispan.functional;

import java.io.Serializable;

/**
 * Entity that has a many-to-one relationship to a Customer
 * 
 * @author Galder Zamarreño
 * @since 3.5
 */
public class Contact implements Serializable {
   Integer id;
   String name;
   String tlf;
   Customer customer;

   public Integer getId() {
      return id;
   }

   public void setId(Integer id) {
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getTlf() {
      return tlf;
   }

   public void setTlf(String tlf) {
      this.tlf = tlf;
   }

   public Customer getCustomer() {
      return customer;
   }

   public void setCustomer(Customer customer) {
      this.customer = customer;
   }

   @Override
   public boolean equals(Object o) {
      if (o == this)
         return true;
      if (!(o instanceof Contact))
         return false;
      Contact c = (Contact) o;
      return c.id.equals(id) && c.name.equals(name) && c.tlf.equals(tlf);
   }

   @Override
   public int hashCode() {
      int result = 17;
      result = 31 * result + (id == null ? 0 : id.hashCode());
      result = 31 * result + name.hashCode();
      result = 31 * result + tlf.hashCode();
      return result;
   }

}
=======
/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat, Inc. and/or it's affiliates or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat, Inc. and/or it's affiliates.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.test.cache.infinispan.functional;
import java.io.Serializable;

/**
 * Entity that has a many-to-one relationship to a Customer
 * 
 * @author Galder Zamarreño
 * @since 3.5
 */
public class Contact implements Serializable {
   Integer id;
   String name;
   String tlf;
   Customer customer;

   public Integer getId() {
      return id;
   }

   public void setId(Integer id) {
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getTlf() {
      return tlf;
   }

   public void setTlf(String tlf) {
      this.tlf = tlf;
   }

   public Customer getCustomer() {
      return customer;
   }

   public void setCustomer(Customer customer) {
      this.customer = customer;
   }

   @Override
   public boolean equals(Object o) {
      if (o == this)
         return true;
      if (!(o instanceof Contact))
         return false;
      Contact c = (Contact) o;
      return c.id.equals(id) && c.name.equals(name) && c.tlf.equals(tlf);
   }

   @Override
   public int hashCode() {
      int result = 17;
      result = 31 * result + (id == null ? 0 : id.hashCode());
      result = 31 * result + name.hashCode();
      result = 31 * result + tlf.hashCode();
      return result;
   }

}
>>>>>>> Changes from requests at Hibernate meeting: message codes, use XXXf methods for debug and trace, use @Cause
=======
/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.cache.infinispan.functional;
import java.io.Serializable;

/**
 * Entity that has a many-to-one relationship to a Customer
 * 
 * @author Galder Zamarreño
 * @since 3.5
 */
public class Contact implements Serializable {
   Integer id;
   String name;
   String tlf;
   Customer customer;

   public Integer getId() {
      return id;
   }

   public void setId(Integer id) {
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getTlf() {
      return tlf;
   }

   public void setTlf(String tlf) {
      this.tlf = tlf;
   }

   public Customer getCustomer() {
      return customer;
   }

   public void setCustomer(Customer customer) {
      this.customer = customer;
   }

   @Override
   public boolean equals(Object o) {
      if (o == this)
         return true;
      if (!(o instanceof Contact))
         return false;
      Contact c = (Contact) o;
      return c.id.equals(id) && c.name.equals(name) && c.tlf.equals(tlf);
   }

   @Override
   public int hashCode() {
      int result = 17;
      result = 31 * result + (id == null ? 0 : id.hashCode());
      result = 31 * result + name.hashCode();
      result = 31 * result + tlf.hashCode();
      return result;
   }

}
>>>>>>> HHH-9803 - Checkstyle fix ups - headers
