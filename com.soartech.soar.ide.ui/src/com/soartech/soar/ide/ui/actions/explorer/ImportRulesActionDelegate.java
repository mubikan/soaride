package com.soartech.soar.ide.ui.actions.explorer;

import java.io.File;
import java.util.ArrayList;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewActionDelegate;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

import com.soartech.soar.ide.core.sql.ISoarDatabaseTreeItem;
import com.soartech.soar.ide.core.sql.SoarDatabaseJoinFolder;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow;
import com.soartech.soar.ide.core.sql.SoarDatabaseRowFolder;
import com.soartech.soar.ide.core.sql.SoarDatabaseUtil;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow.Table;

public class ImportRulesActionDelegate implements IViewActionDelegate {

	StructuredSelection ss;

	@Override
	public void run(IAction action) {
		Shell shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
		if (ss != null) {
			Object element = ss.getFirstElement();
			if (element != null) {
				if (element instanceof ISoarDatabaseTreeItem) {
					ISoarDatabaseTreeItem item = (ISoarDatabaseTreeItem) element;
					SoarDatabaseRow row = item.getRow();
					SoarDatabaseRow agent = row.getAncestorRow(Table.AGENTS);
					FileDialog dialog = new FileDialog(shell);
					dialog.setText("Choose a Soar file to import");
					String path = dialog.open();
					File file = new File(path);
					SoarDatabaseUtil.importRules(file, agent);
					return;
				}
			}
		}
		String title = "No Agent Selected";
		org.eclipse.swt.graphics.Image image = shell.getDisplay().getSystemImage(SWT.ICON_QUESTION);
		String message = "Cannot import rules: No agent selected";
		String[] labels = new String[] {"OK"};
		MessageDialog dialog = new MessageDialog(shell, title, image, message, MessageDialog.ERROR, labels, 0);
		dialog.open();
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		if (selection instanceof StructuredSelection) {
			ss = (StructuredSelection)selection;
		}
	}

	@Override
	public void init(IViewPart arg0) {
	}
}