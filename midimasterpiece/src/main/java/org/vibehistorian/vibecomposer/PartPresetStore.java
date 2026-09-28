package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.Wrappers.InstPartsWrapper;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads and writes instrument part preset files. */
public final class PartPresetStore {
	private static final String PRESET_DIRECTORY = "PartPresets";

	public static final class PresetFile {
		private final String name;
		private final int partCount;

		private PresetFile(String name, int partCount) {
			this.name = name;
			this.partCount = partCount;
		}

		public String getName() {
			return name;
		}

		public int getPartCount() {
			return partCount;
		}
	}

	public List<PresetFile> listPresets(INST instrument) throws IOException {
		File folder = getInstrumentDirectory(instrument);
		if (!folder.exists()) return Collections.emptyList();
		File[] files = folder.listFiles();
		if (files == null) return Collections.emptyList();

		List<PresetFile> presets = new ArrayList<>();
		String closingTag = "</" + Constants.instPartNames[instrument.getIndex()] + "Part>";
		for (File file : files) {
			if (!file.isFile()) continue;
			String name = file.getName();
			int extension = name.lastIndexOf(".");
			if (extension > 0 && extension < name.length() - 1) name = name.substring(0, extension);
			presets.add(new PresetFile(name, countStringOccurrences(file, closingTag)));
		}
		return presets;
	}

	public void save(INST instrument, String name, List<? extends InstPart> parts)
			throws JAXBException {
		File folder = getInstrumentDirectory(instrument);
		folder.mkdirs();

		Class<? extends InstPartsWrapper> wrapperClass =
				InstPartsWrapper.getWrapperClass(instrument.getIndex());
		JAXBContext context = JAXBContext.newInstance(wrapperClass, InstPartsWrapper.class);
		Marshaller marshaller = context.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		InstPartsWrapper<?> wrapper = InstPartsWrapper.forClass(wrapperClass);
		wrapper.setParts(parts);
		File file = new File(folder, name + ".xml");
		marshaller.marshal(wrapper, file);
		LG.i("File saved: " + file.getPath());
	}

	public List<InstPart> load(INST instrument, String name) throws JAXBException, IOException {
		File file = new File(getInstrumentDirectory(instrument), name + ".xml");
		Class<? extends InstPartsWrapper> wrapperClass =
				InstPartsWrapper.getWrapperClass(instrument.getIndex());
		JAXBContext context = JAXBContext.newInstance(wrapperClass, InstPartsWrapper.class);
		try (FileReader reader = new FileReader(file)) {
			InstPartsWrapper<?> wrapper =
					(InstPartsWrapper<?>) context.createUnmarshaller().unmarshal(reader);
			return new ArrayList<>(wrapper.getParts());
		}
	}

	private File getInstrumentDirectory(INST instrument) {
		return new File(PRESET_DIRECTORY, Constants.instNames[instrument.getIndex()]);
	}

	private static int countStringOccurrences(File file, String searchString) throws IOException {
		if (file == null || !file.exists() || searchString == null || searchString.isEmpty()) {
			throw new IllegalArgumentException("Invalid file or search string.");
		}

		int count = 0;
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				count += countOccurrencesInLine(line, searchString);
			}
		}
		return count;
	}

	private static int countOccurrencesInLine(String line, String searchString) {
		int count = 0;
		int index = 0;
		while ((index = line.indexOf(searchString, index)) != -1) {
			count++;
			index += searchString.length();
		}
		return count;
	}
}
