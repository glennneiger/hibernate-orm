/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import org.hibernate.AssertionFailure;
import org.hibernate.boot.AttributeConverterInfo;
import org.hibernate.boot.archive.scan.internal.StandardScanOptions;
import org.hibernate.boot.archive.scan.spi.ScanEnvironment;
import org.hibernate.boot.archive.scan.spi.ScanOptions;
import org.hibernate.boot.archive.spi.ArchiveDescriptorFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.ClassLoaderAccess;
import org.hibernate.boot.spi.MetadataBuildingOptions;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.config.spi.ConfigurationService;

import org.jboss.logging.Logger;

import static org.hibernate.internal.log.DeprecationLogger.DEPRECATION_LOGGER;

/**
 * @author Andrea Boriero
 */
public class BootstrapContextImpl implements BootstrapContext {
	private static final Logger log = Logger.getLogger( BootstrapContextImpl.class );

	private final StandardServiceRegistry serviceRegistry;
	private final ClassmateContext classmateContext;
	private final MetadataBuildingOptions metadataBuildingOptions;
	private HashMap<Class,AttributeConverterInfo> attributeConverterInfoMap;
	private final ClassLoaderAccessImpl classLoaderAccess;

	private ScanOptions scanOptions;
	private ScanEnvironment scanEnvironment;
	private Object scannerSetting;

	private ArchiveDescriptorFactory archiveDescriptorFactory;

	public BootstrapContextImpl(
			StandardServiceRegistry serviceRegistry,
			ClassmateContext classmateContext,
			MetadataBuildingOptions metadataBuildingOptions) {
		this.serviceRegistry = serviceRegistry;
		this.classmateContext = classmateContext;
		this.metadataBuildingOptions = metadataBuildingOptions;

		final ClassLoaderService classLoaderService = serviceRegistry.getService( ClassLoaderService.class );
		this.classLoaderAccess = new ClassLoaderAccessImpl( classLoaderService );

		final StrategySelector strategySelector = serviceRegistry.getService( StrategySelector.class );
		final ConfigurationService configService = serviceRegistry.getService( ConfigurationService.class );

		this.scanOptions = new StandardScanOptions(
				(String) configService.getSettings().get( AvailableSettings.SCANNER_DISCOVERY ),
				false
		);

		// ScanEnvironment must be set explicitly
		this.scannerSetting = configService.getSettings().get( AvailableSettings.SCANNER );
		if ( this.scannerSetting == null ) {
			this.scannerSetting = configService.getSettings().get( AvailableSettings.SCANNER_DEPRECATED );
			if ( this.scannerSetting != null ) {
				DEPRECATION_LOGGER.logDeprecatedScannerSetting();
			}
		}
		this.archiveDescriptorFactory = strategySelector.resolveStrategy(
				ArchiveDescriptorFactory.class,
				configService.getSettings().get( AvailableSettings.SCANNER_ARCHIVE_INTERPRETER )
		);
	}

	@Override
	public StandardServiceRegistry getServiceRegistry() {
		return serviceRegistry;
	}

	@Override
	public MetadataBuildingOptions getMetadataBuildingOptions() {
		return metadataBuildingOptions;
	}

	@Override
	public ClassLoader getJpaTempClassLoader() {
		return classLoaderAccess.getJpaTempClassLoader();
	}

	@Override
	public ClassLoaderAccess getClassLoaderAccess() {
		return classLoaderAccess;
	}

	@Override
	public ClassmateContext getClassmateContext() {
		return classmateContext;
	}

	@Override
	public ArchiveDescriptorFactory getArchiveDescriptorFactory() {
		return archiveDescriptorFactory;
	}

	@Override
	public ScanOptions getScanOptions() {
		return scanOptions;
	}

	@Override
	public ScanEnvironment getScanEnvironment() {
		return scanEnvironment;
	}

	@Override
	public Object getScanner() {
		return scannerSetting;
	}

	@Override
	public Collection<AttributeConverterInfo> getAttributeConverters() {
		return attributeConverterInfoMap != null
				? new ArrayList<>( attributeConverterInfoMap.values() )
				: Collections.emptyList();
	}

	@Override
	public void release() {
		classmateContext.release();
		classLoaderAccess.release();

		scanOptions = null;
		scanEnvironment = null;
		scannerSetting = null;
		archiveDescriptorFactory = null;
//		jandexView = null;
//
//		if ( sqlFunctionMap != null ) {
//			sqlFunctionMap.clear();
//		}
//
//		if ( auxiliaryDatabaseObjectList != null ) {
//			auxiliaryDatabaseObjectList.clear();
//		}
//
//		if ( attributeConverterDefinitionsByClass != null ) {
//			attributeConverterDefinitionsByClass.clear();
//		}
//
//		if ( cacheRegionDefinitions != null ) {
//			cacheRegionDefinitions.clear();
//		}
	}

	public void addAttributeConverterInfo(AttributeConverterInfo info) {
		if ( this.attributeConverterInfoMap == null ) {
			this.attributeConverterInfoMap = new HashMap<>();
		}

		final Object old = this.attributeConverterInfoMap.put( info.getConverterClass(), info );

		if ( old != null ) {
			throw new AssertionFailure(
					String.format(
							"AttributeConverter class [%s] registered multiple times",
							info.getConverterClass()
					)
			);
		}
	}

	void injectScanEnvironment(ScanEnvironment scanEnvironment) {
		if ( log.isDebugEnabled() ) {
			log.debugf(
					"Injecting ScanEnvironment [%s] into BootstrapContext; was [%s]",
					scanEnvironment,
					this.scanEnvironment
			);
		}
		this.scanEnvironment = scanEnvironment;
	}
}
