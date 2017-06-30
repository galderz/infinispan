/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat, Inc. and/or it's affiliates, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.hibernate.cache.infinispan.tm;
<<<<<<< HEAD
<<<<<<< HEAD
import java.util.Properties;
=======

>>>>>>> HHH-8159 - Apply fixups indicated by analysis tools
import javax.transaction.TransactionManager;
import java.util.Properties;

import org.hibernate.cfg.Settings;
<<<<<<< HEAD
import org.hibernate.transaction.TransactionManagerLookup;
=======

import org.hibernate.cfg.Settings;
import org.hibernate.service.jta.platform.spi.JtaPlatform;
=======
import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform;
>>>>>>> HHH-7556 - Clean up packages

import javax.transaction.TransactionManager;
import java.util.Properties;
>>>>>>> HHH-5949 - Migrate, complete and integrate TransactionFactory as a service

/**
 * HibernateTransactionManagerLookup.
 *
 * @author Galder Zamarreño
 * @since 3.5
 */
public class HibernateTransactionManagerLookup implements org.infinispan.transaction.lookup.TransactionManagerLookup {
	private final JtaPlatform jtaPlatform;

	public HibernateTransactionManagerLookup(Settings settings, Properties properties) {
		this.jtaPlatform = settings != null ? settings.getJtaPlatform() : null;
	}

	@Override
	public TransactionManager getTransactionManager() throws Exception {
		return jtaPlatform == null ? null : jtaPlatform.retrieveTransactionManager();
	}

}
