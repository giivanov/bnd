package aQute.lib.deployer.obr;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.osgi.service.bindex.BundleIndexer;
import org.osgi.service.coordinator.Coordination;
import org.osgi.service.coordinator.Coordinator;
import org.osgi.service.coordinator.Participant;

import aQute.bnd.service.Refreshable;
import aQute.lib.deployer.FileRepo;
import aQute.lib.io.IO;
import aQute.lib.osgi.Jar;
import aQute.libg.reporter.Reporter;
import aQute.libg.version.Version;

public class LocalOBR extends OBR implements Refreshable, Participant {
	
	public static final String PROP_LOCAL_DIR = "local";
	public static final String PROP_READONLY = "readonly";

	private final FileRepo storageRepo = new FileRepo();
	
	private File storageDir;
	private File localIndex;
	
	private List<URL> indexUrls;

	// @GuardedBy("this")
	private File newFileInCoordination;

	@Override
	public void setReporter(Reporter reporter) {
		super.setReporter(reporter);
		storageRepo.setReporter(reporter);
	}

	@Override
	public void setProperties(Map<String, String> map) {
		super.setProperties(map);
		
		// Load essential properties
		String localDirPath = map.get(PROP_LOCAL_DIR);
		if (localDirPath == null)
			throw new IllegalArgumentException(String.format("Attribute '%s' must be set on LocalOBR plugin.", PROP_LOCAL_DIR));
		storageDir = new File(localDirPath);
		if (!storageDir.isDirectory())
			throw new IllegalArgumentException(String.format("Local path '%s' does not exist or is not a directory.", localDirPath));
		
		// Configure the storage repository
		Map<String, String> storageRepoConfig = new HashMap<String, String>(2);
		storageRepoConfig.put(FileRepo.LOCATION, localDirPath);
		storageRepoConfig.put(FileRepo.READONLY, map.get(PROP_READONLY));
		storageRepo.setProperties(storageRepoConfig);
		
		// Set the local index and cache directory locations
		localIndex = new File(storageDir, REPOSITORY_FILE_NAME);
		if (localIndex.exists() && !localIndex.isFile())
			throw new IllegalArgumentException(String.format("Cannot build local repository index: '%s' already exists but is not a plain file.", localIndex.getAbsolutePath()));
		cacheDir = new File(storageDir, ".obrcache");
		if (cacheDir.exists() && !cacheDir.isDirectory())
			throw new IllegalArgumentException(String.format("Cannot create repository cache: '%s' already exists but is not directory.", cacheDir.getAbsolutePath()));
	}
	
	@Override
	protected void initialiseIndexes() throws Exception {
		if (!localIndex.exists()) {
			regenerateIndex();
		}
		try {
			Collection<URL> remotes = super.getOBRIndexes();
			indexUrls = new ArrayList<URL>(remotes.size() + 1);
			indexUrls.add(localIndex.toURI().toURL());
			indexUrls.addAll(remotes);
		} catch (IOException e) {
			throw new IllegalArgumentException("Error initialising local index URL", e);
		}
	}
	
	private synchronized void regenerateIndex() throws Exception {
		BundleIndexer indexer = registry.getPlugin(BundleIndexer.class);
		if (indexer == null)
			throw new IllegalStateException("Cannot index repository: no Bundle Indexer service or plugin found.");
		
		Set<File> allFiles = new HashSet<File>();
		gatherFiles(allFiles);
		
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(localIndex);
			if (!allFiles.isEmpty()) {
				Map<String, String> config = new HashMap<String, String>();
				config.put(BundleIndexer.REPOSITORY_NAME, this.getName());
				config.put(BundleIndexer.ROOT_URL, localIndex.getCanonicalFile().toURI().toURL().toString());
				indexer.index(allFiles, out, config);
			} else {
				ByteArrayInputStream emptyRepo = new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?>\n<repository lastmodified='0'/>".getBytes());
				IO.copy(emptyRepo, out);
			}
		} finally {
			out.close();
		}
	}

	private void gatherFiles(Set<File> allFiles) throws Exception {
		List<String> bsns = storageRepo.list(null);
		if (bsns != null) for (String bsn : bsns) {
			List<Version> versions = storageRepo.versions(bsn);
			if (versions != null) for (Version version : versions) {
				File file = storageRepo.get(bsn, version.toString(), Strategy.HIGHEST, null);
				if (file != null)
					allFiles.add(file.getCanonicalFile());
			}
		}
	}

	@Override
	public List<URL> getOBRIndexes() {
		return indexUrls;
	}
	
	@Override
	public boolean canWrite() {
		return storageRepo.canWrite();
	}
	
	private void beginPut(Jar jar) throws Exception {
		File newFile = storageRepo.put(jar);
		synchronized (this) {
			newFileInCoordination = newFile;
		}
	}
	
	private synchronized void finishPut() throws Exception {
		regenerateIndex();
	}
	
	public synchronized void ended(Coordination coordination) throws Exception {
		finishPut();
	}
	
	public void failed(Coordination coordination) throws Exception {
		newFileInCoordination.delete();
	}
	
	@Override
	public synchronized File put(Jar jar) throws Exception {
		Coordinator coordinator = registry.getPlugin(Coordinator.class);
		if (coordinator != null && coordinator.addParticipant(this)) {
			beginPut(jar);
		} else {
			beginPut(jar);
			finishPut();
		}
		return newFileInCoordination;
	}

	public boolean refresh() {
		reset();
		return true;
	}

	public File getRoot() {
		return storageDir;
	}
}
