package com.soartech.soar.ide.ui.editors.database;

import java.util.ArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.part.EditorPart;

import com.soartech.soar.ide.core.sql.EditableColumn;
import com.soartech.soar.ide.core.sql.ISoarDatabaseTreeItem;
import com.soartech.soar.ide.core.sql.SoarDatabaseEditorInput;
import com.soartech.soar.ide.core.sql.SoarDatabaseJoinFolder;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow.Table;
import com.soartech.soar.ide.ui.views.SoarLabelProvider;
import com.soartech.soar.ide.ui.views.explorer.SoarExplorerDatabaseContentProvider;

public class SoarDatabaseDatamapEditor extends EditorPart {

	public static final String ID = "com.soartech.soar.ide.ui.editors.database.SoarDatabaseDatamapEditor";

	private SoarDatabaseRow row;
	private TreeViewer tree;

	@Override
	public void doSave(IProgressMonitor monitor) {

	}

	@Override
	public void doSaveAs() {

	}

	@Override
	public void init(IEditorSite site, IEditorInput input)
			throws PartInitException {
		setSite(site);
		setInput(input);
		if (input instanceof SoarDatabaseEditorInput) {
			row = ((SoarDatabaseEditorInput) input).getRow();
		} else {
			throw new PartInitException(
					"Input not instance of SoarDatabaseEditorInput");
		}
	}

	@Override
	public boolean isDirty() {
		return false;
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

	@Override
	public void createPartControl(Composite parent) {
		tree = new TreeViewer(parent, SWT.NONE);

		tree.setContentProvider(new SoarExplorerDatabaseContentProvider(false,
				false, false, true, false, true));
		tree.setLabelProvider(SoarLabelProvider.createFullLabelProvider(null));
		tree.setInput(row);

		MenuManager manager = new MenuManager();
		manager.addMenuListener(new IMenuListener() {

			@Override
			public void menuAboutToShow(IMenuManager manager) {
				manager.removeAll();
				ISelection selection = tree.getSelection();
				if (selection instanceof IStructuredSelection) {
					IStructuredSelection ss = (IStructuredSelection) selection;
					Object element = ss.getFirstElement();

					if (element instanceof SoarDatabaseRow) {
						final SoarDatabaseRow row = (SoarDatabaseRow) element;

						ArrayList<Table> childTables = row.getChildTables();

						Table table = row.getTable();
						final String tableName = table.shortName();
						final String rowName = row.getName();

						for (final Table t : childTables) {
							final String childTableName = t.tableName();
							manager
									.add(new Action("Add New " + childTableName) {
										@Override
										public void run() {

											Shell shell = PlatformUI
													.getWorkbench()
													.getActiveWorkbenchWindow()
													.getShell();
											String title = "New "
													+ childTableName;
											String message = "Enter Name:";
											String initialValue = "New "
													+ childTableName;
											InputDialog dialog = new InputDialog(
													shell, title, message,
													initialValue, null);
											dialog.open();
											String result = dialog.getValue();

											if (result != null
													&& result.length() > 0) {
												row.createChild(t, result);
												refreshTree();
											}
										}
									});
						}

						manager.add(new Action("Rename " + rowName) {
							@Override
							public void run() {
								Shell shell = PlatformUI.getWorkbench()
										.getActiveWorkbenchWindow().getShell();
								String title = "Rename " + tableName;
								String message = "Enter New Name:";
								String initialValue = row.getName();
								InputDialog dialog = new InputDialog(shell,
										title, message, initialValue, null);
								dialog.open();
								String result = dialog.getValue();

								if (result != null && result.length() > 0) {
									row.setName(result);
									refreshTree();
								}
							}
						});

						manager.add(new Action("Delete " + rowName) {
							@Override
							public void run() {
								row.deleteAllChildren(true);
								refreshTree();
							}
						});

						ArrayList<EditableColumn> editableColumns = row
								.getEditableColumns();
						for (final EditableColumn column : editableColumns) {
							manager.add(new Action("Edit " + column.getName()) {
								@Override
								public void run() {
									Shell shell = PlatformUI.getWorkbench()
											.getActiveWorkbenchWindow()
											.getShell();
									String title = "Edit " + column.getName();
									String message = "Enter New Value:";
									Object currentValue = row
											.getEditableColumnValue(column);
									String initialValue = null;
									if (currentValue == null) {
										initialValue = "";
									} else {
										initialValue = currentValue.toString();
									}
									InputDialog dialog = new InputDialog(shell,
											title, message, initialValue, null);
									dialog.open();
									String result = dialog.getValue();

									if (result != null) {
										EditableColumn.Type columnType = column
												.getType();
										Object parsedValue = null;
										switch (columnType) {
										case FLOAT:
											parsedValue = Float
													.parseFloat(result);
											break;
										case INTEGER:
											parsedValue = Integer
													.parseInt(result);
											break;
										case STRING:
											parsedValue = result;
											break;
										}

										if (parsedValue != null
												&& result.length() > 0) {
											row.editColumnValue(column,
													parsedValue);
											refreshTree();
										}
									}
								}
							});
						}

						final ArrayList<ISoarDatabaseTreeItem> folders = row
								.getDirectedJoinedChildren(true);
						if (folders.size() > 0) {

							// Create content provider for choosing which type
							// of attribute
							final IStructuredContentProvider attributeTypeContentProvider = new IStructuredContentProvider() {

								@Override
								public void inputChanged(Viewer arg0,
										Object arg1, Object arg2) {
								}

								@Override
								public void dispose() {
								}

								@Override
								public Object[] getElements(Object arg0) {
									return folders.toArray();
								}
							};

							manager.add(new Action("Add New Attribute") {
								@Override
								public void run() {
									Shell shell = PlatformUI.getWorkbench()
											.getActiveWorkbenchWindow()
											.getShell();
									ListDialog listDialog = new ListDialog(
											shell);
									listDialog
											.setContentProvider(attributeTypeContentProvider);
									listDialog
											.setLabelProvider(SoarLabelProvider
													.createFullLabelProvider(null));
									listDialog.setInput(new Object());
									listDialog.open();
									Object[] result = listDialog.getResult();
									if (result[0] instanceof SoarDatabaseJoinFolder) {
										Table resultTable = ((SoarDatabaseJoinFolder) result[0])
												.getTable();
										String title = "New "
												+ resultTable.shortName();
										String message = "Enter Name:";
										String initialValue = "New "
												+ resultTable.shortName();
										InputDialog dialog = new InputDialog(
												shell, title, message,
												initialValue, null);
										dialog.open();
										String resultString = dialog.getValue();
										if (resultString != null
												&& resultString.length() > 0) {
											row.createJoinedChild(resultTable,
													resultString);
											refreshTree();
											tree.setExpandedState(row, true);
										}
									}
								}
							});

							// Create content provider for choosing an existing
							// attribute
							final IStructuredContentProvider existingAttributeContentProvider = new IStructuredContentProvider() {

								@Override
								public void inputChanged(Viewer arg0,
										Object arg1, Object arg2) {
								}

								@Override
								public void dispose() {
								}

								@Override
								public Object[] getElements(Object input) {
									if (input instanceof SoarDatabaseRow) {
										SoarDatabaseRow problemSpace = row
												.getAncestorRow(Table.PROBLEM_SPACES);
										if (problemSpace != null) {
											ArrayList<ISoarDatabaseTreeItem> rootDatamapNodes = problemSpace
													.getChildrenOfType(Table.DATAMAP_IDENTIFIERS);
											if (rootDatamapNodes.size() > 0
													&& rootDatamapNodes.get(0) instanceof SoarDatabaseRow) {
												SoarDatabaseRow rootDatamapNode = (SoarDatabaseRow) rootDatamapNodes
														.get(0);
												ArrayList<ISoarDatabaseTreeItem> descendants = rootDatamapNode
														.getDescendantsOfType(Table.DATAMAP_IDENTIFIERS);
												return descendants.toArray();
											}
										} else {
											try {
												throw new Exception(
														"Ancestor row of type PROBLEM_SPACES not found: "
																+ row);
											} catch (Exception e) {
												e.printStackTrace();
											}
										}
									}
									return new Object[] {};
								}
							};

							manager.add(new Action("Link Existing Attribute") {
								@Override
								public void run() {
									Shell shell = PlatformUI.getWorkbench()
											.getActiveWorkbenchWindow()
											.getShell();
									ListDialog listDialog = new ListDialog(
											shell);
									listDialog
											.setContentProvider(existingAttributeContentProvider);
									listDialog
											.setLabelProvider(SoarLabelProvider
													.createFullLabelProvider(null));
									listDialog.setInput(row);
									listDialog.open();
									Object[] result = listDialog.getResult();
									if (result != null
											&& result.length > 0
											&& result[0] instanceof SoarDatabaseRow) {
										SoarDatabaseRow child = (SoarDatabaseRow) result[0];
										SoarDatabaseRow.directedJoinRows(row,
												child,
												row.getDatabaseConnection());
										refreshTree();
										tree.setExpandedState(row, true);
									}
								};
							});

							// Create content provider for choosing an attribute
							// that is already linked
							final IStructuredContentProvider linkedAttributeContentProvider = new IStructuredContentProvider() {

								@Override
								public void inputChanged(Viewer arg0,
										Object arg1, Object arg2) {
								}

								@Override
								public void dispose() {
								}

								@Override
								public Object[] getElements(Object input) {
									if (input instanceof SoarDatabaseRow) {
										SoarDatabaseRow sdr = (SoarDatabaseRow) input;
										ArrayList<ISoarDatabaseTreeItem> children = sdr
												.getDirectedJoinedChildren(false);
										return children.toArray();
									}
									return new Object[] {};
								}
							};

							manager.add(new Action("Remove Linked Attribute") {
								@Override
								public void run() {
									Shell shell = PlatformUI.getWorkbench()
											.getActiveWorkbenchWindow()
											.getShell();
									ListDialog listDialog = new ListDialog(
											shell);
									listDialog
											.setContentProvider(linkedAttributeContentProvider);
									listDialog
											.setLabelProvider(SoarLabelProvider
													.createFullLabelProvider(null));
									listDialog.setInput(row);
									listDialog.open();
									Object[] result = listDialog.getResult();
									if (result != null
											&& result.length > 0
											&& result[0] instanceof SoarDatabaseRow) {
										SoarDatabaseRow child = (SoarDatabaseRow) result[0];
										SoarDatabaseRow.directedUnjoinRows(row,
												child,
												row.getDatabaseConnection());
										refreshTree();
										tree.setExpandedState(row, true);
									}
								};
							});
						}
					}
				}
			}
		});

		Menu menu = manager.createContextMenu(tree.getTree());
		tree.getTree().setMenu(menu);
	}

	@Override
	public void setFocus() {
	}

	// Convenience method for refreshing tree
	private void refreshTree() {
		Runnable runnable = new Runnable() {

			@Override
			public void run() {
				try {
					Object[] elements = tree.getExpandedElements();
					TreePath[] paths = tree.getExpandedTreePaths();
					tree.setInput(row);
					tree.setExpandedTreePaths(paths);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		};
		
		Display.findDisplay(Thread.currentThread()).asyncExec(runnable);
	}
}
