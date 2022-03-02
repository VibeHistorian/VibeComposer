package org.vibehistorian.vibecomposer.Helpers;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.JComponent;
import javax.swing.TransferHandler;

public class FileTransferHandler extends TransferHandler {
	private static final long serialVersionUID = 8715658270565272058L;

	private Function<? super Object, File> fileGenerator = null;

	public FileTransferHandler(Function<? super Object, File> fileGen) {
		fileGenerator = fileGen;
	}

	@Override
	protected Transferable createTransferable(JComponent c) {
		List<File> files = new ArrayList<>();
		files.add(fileGenerator.apply(new Object()));
		return new FileTransferable(files);
	}

	@Override
	public int getSourceActions(JComponent c) {
		return COPY_OR_MOVE;
	}
}
