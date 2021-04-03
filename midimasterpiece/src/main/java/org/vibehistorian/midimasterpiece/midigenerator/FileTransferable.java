package org.vibehistorian.midimasterpiece.midigenerator;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class FileTransferable implements Transferable {
	
	private final List<File> listOfFiles;
	
	public FileTransferable(List<File> listOfFiles) {
		this.listOfFiles = listOfFiles;
	}
	
	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return new DataFlavor[] { DataFlavor.javaFileListFlavor };
	}
	
	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return DataFlavor.javaFileListFlavor.equals(flavor);
	}
	
	@Override
	public Object getTransferData(DataFlavor flavor)
			throws UnsupportedFlavorException, IOException {
		if (!isDataFlavorSupported(flavor)) {
			throw new UnsupportedFlavorException(flavor);
		}
		return listOfFiles;
	}
}
