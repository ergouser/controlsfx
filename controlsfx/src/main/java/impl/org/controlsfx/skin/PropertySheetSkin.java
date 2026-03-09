/**
 * Copyright (c) 2013, 2016 ControlsFX
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *     * Neither the name of ControlsFX, any associated website, nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL CONTROLSFX BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package impl.org.controlsfx.skin;

import static impl.org.controlsfx.i18n.Localization.asKey;
import static impl.org.controlsfx.i18n.Localization.localize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SkinBase;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import javafx.scene.layout.StackPane;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;
import org.controlsfx.control.PropertySheet.Mode;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.property.editor.AbstractPropertyEditor;
import org.controlsfx.property.editor.PropertyEditor;

public class PropertySheetSkin extends SkinBase<PropertySheet> {
    
    /**************************************************************************
     * <p>
     * Static fields
     * 
     **************************************************************************/

    private static final int MIN_COLUMN_WIDTH = 100;
    private static final int DIVIDER_WIDTH = 4;
    
    /**************************************************************************
     * <p>
     * fields
     * 
     **************************************************************************/
    
    private final BorderPane content;
    private final ScrollPane scroller;
    private final ToolBar toolbar;
    private final SegmentedButton modeButton = ActionUtils.createSegmentedButton(
        new ActionChangeMode(Mode.NAME),
        new ActionChangeMode(Mode.CATEGORY)
    );
    private final TextField searchField = TextFields.createClearableTextField();
    
    
    /**************************************************************************
     * <p>
     * Constructors
     * 
     **************************************************************************/

    public PropertySheetSkin(final PropertySheet control) {
        super(control);
        
        scroller = new ScrollPane();
        scroller.setFitToWidth(true);
        
        toolbar = new ToolBar();
        toolbar.managedProperty().bind(toolbar.visibleProperty());
        toolbar.setFocusTraversable(true);
        
        // property sheet mode
        modeButton.managedProperty().bind(modeButton.visibleProperty());
        modeButton.getButtons().get(getSkinnable().modeProperty().get().ordinal()).setSelected(true);
        toolbar.getItems().add(modeButton);
        
        // property sheet search
        searchField.setPromptText( localize(asKey("property.sheet.search.field.prompt"))); //$NON-NLS-1$
        searchField.setMinWidth(0);
        HBox.setHgrow(searchField, Priority.SOMETIMES);
        searchField.managedProperty().bind(searchField.visibleProperty());
        toolbar.getItems().add(searchField);
        
        // layout controls
        content = new BorderPane();
        content.setTop(toolbar);
        content.setCenter(scroller);
        getChildren().add(content);
              
        
        // setup listeners
        registerChangeListener(control.modeProperty(), e -> refreshProperties());
        registerChangeListener(control.propertyEditorFactory(), e -> refreshProperties());
        registerChangeListener(control.titleFilter(), e -> refreshProperties());
        registerChangeListener(searchField.textProperty(), e -> getSkinnable().setTitleFilter(searchField.getText()));
        registerChangeListener(control.modeSwitcherVisibleProperty(), e -> updateToolbar());
        registerChangeListener(control.searchBoxVisibleProperty(), e -> updateToolbar());
        registerChangeListener(control.categoryComparatorProperty(), e -> refreshProperties());
        
        control.getItems().addListener((ListChangeListener<Item>) change -> refreshProperties());
        
        // initialize properly 
        refreshProperties(); 
        updateToolbar();
    }


    /**************************************************************************
     * <p>
     * Overriding public API
     * 
     **************************************************************************/

    @Override protected void layoutChildren(double x, double y, double w, double h) {
        content.resizeRelocate(x, y, w, h);
    }



    /**************************************************************************
     * <p>
     * Implementation
     * 
     **************************************************************************/
    
    private void updateToolbar() {
        modeButton.setVisible(getSkinnable().isModeSwitcherVisible());
        searchField.setVisible(getSkinnable().isSearchBoxVisible());
        
        toolbar.setVisible(modeButton.isVisible() || searchField.isVisible());
    }

    private void refreshProperties() {
        scroller.setContent(buildPropertySheetContainer());
    }
    
    private Node buildPropertySheetContainer() {
      //noinspection SwitchStatementWithTooFewBranches
      switch( getSkinnable().modeProperty().get() ) {
            case CATEGORY: {
                // group by category
                Map<String, List<Item>> categoryMap = new TreeMap<>(getSkinnable().getCategoryComparator());
                for (Item p: getSkinnable().getItems()) {
                    String category = p.getCategory();
                  List<Item> list = categoryMap.computeIfAbsent(category, k -> new ArrayList<>());
                  list.add(p);
                }
                
                // create category-based accordion
                Accordion accordion = new Accordion();
                for( String category: categoryMap.keySet() ) {
                	PropertyPane props = new PropertyPane( categoryMap.get(category));
                	// Only show non-empty categories 
                	if (!props.getChildrenUnmodifiable().isEmpty()) {
                       TitledPane pane = new TitledPane( category, props );
                       pane.setExpanded(true);
                       accordion.getPanes().add(pane);
                    }
                }
                if (!accordion.getPanes().isEmpty()) {
                    accordion.setExpandedPane(accordion.getPanes().get(0));
                }
                return accordion;
            }
            
            default: return new PropertyPane(getSkinnable().getItems());
        }
        
    }

    
    /**************************************************************************
     * <p>
     * Support classes / enums
     * 
     **************************************************************************/
    
    private class ActionChangeMode extends Action {
        
    	@SuppressWarnings("FieldCanBeLocal")
      private final Image CATEGORY_IMAGE = new Image(Objects.requireNonNull(
          PropertySheetSkin.class.getResource("/org/controlsfx/control/format-indent-more.png"
      )).toExternalForm()); //$NON-NLS-1$
      @SuppressWarnings("FieldCanBeLocal")
    	private final Image NAME_IMAGE = new Image(Objects.requireNonNull(
          PropertySheetSkin.class.getResource("/org/controlsfx/control/format-line-spacing-triple.png"
      )).toExternalForm()); //$NON-NLS-1$
    	
        public ActionChangeMode(PropertySheet.Mode mode) {
            super(""); //$NON-NLS-1$
            setEventHandler(ae -> getSkinnable().modeProperty().set(mode));
            
            if (mode == Mode.CATEGORY) {
                setGraphic( new ImageView(CATEGORY_IMAGE));
                setLongText(localize(asKey("property.sheet.group.mode.bycategory"))); //$NON-NLS-1$
            } else if (mode == Mode.NAME) {
                setGraphic(new ImageView(NAME_IMAGE));
                setLongText(localize(asKey("property.sheet.group.mode.byname"))); //$NON-NLS-1$
            } else {
                setText("???"); //$NON-NLS-1$
            }
        }

    }


    private class PropertyPane extends GridPane {

        private final BooleanProperty dividerHovered = new SimpleBooleanProperty(false);
        private final DoubleProperty col0Width = new SimpleDoubleProperty(MIN_COLUMN_WIDTH);
        private boolean draggingDivider = false;
        private double dragStartX;
        private double col0StartWidth;

        public PropertyPane(List<Item> properties) {
            this(properties, 0);
        }

        public PropertyPane(List<Item> properties, int nestingLevel) {
            setHgap(5);
            setPadding(new Insets(5, 15, 5, 15 + nestingLevel * 10));
            getStyleClass().add("property-pane"); //$NON-NLS-1$

            // No ColumnConstraints — we control widths manually
            setItems(properties);
        }

        public void setItems(List<Item> properties) {
            getChildren().clear();

            String filter = getSkinnable().titleFilter().get();
            filter = filter == null ? "" : filter.trim().toLowerCase(); //$NON-NLS-1$

            int row = 0;

            for (Item item : properties) {

                String title = item.getName();
                if (!filter.isEmpty() && !title.toLowerCase().contains(filter)) continue;

                // Label with width bound to col0Width
                Label label = new Label(title);
                label.setMinWidth(MIN_COLUMN_WIDTH);
                label.prefWidthProperty().bind(col0Width);
                label.setMaxWidth(Double.MAX_VALUE);

                String description = item.getDescription();
                if (description != null && !description.trim().isEmpty()) {
                    label.setTooltip(new Tooltip(description));
                }

                add(label, 0, row);

                // Divider
                Pane divider = new Pane();
                divider.getStyleClass().add("grid-divider");
                if (row == 0) {
                    divider.getStyleClass().add("grid-divider-top");
                } else if (row == properties.size() - 1) {
                    divider.getStyleClass().add("grid-divider-bottom");
                }
                divider.setMinWidth(DIVIDER_WIDTH);
                divider.setPrefWidth(DIVIDER_WIDTH);
                divider.setMaxWidth(DIVIDER_WIDTH);
                divider.setCursor(Cursor.H_RESIZE);

                divider.setOpacity(0);
                divider.opacityProperty().bind(
                    Bindings.when(dividerHovered).then(1.0).otherwise(0.0)
                );

                divider.hoverProperty().addListener((obs, wasHovered, isHovered) -> {
                    if (isHovered) {
                        dividerHovered.set(true);
                    } else {
                        // Only clear if no other divider is hovered
                        boolean anyHovered = getChildren().stream()
                            .filter(n -> n.getStyleClass().contains("grid-divider"))
                            .anyMatch(Node::isHover);
                        dividerHovered.set(draggingDivider || anyHovered);
                    }
                });

                divider.setOnMousePressed(e -> {
                    draggingDivider = true;
                    dragStartX = e.getScreenX();
                    col0StartWidth = col0Width.get();
                    e.consume();
                });

                divider.setOnMouseReleased(e -> {
                    draggingDivider = false;
                    e.consume();
                });

                divider.setOnMouseDragged(e -> {
                    double delta = e.getScreenX() - dragStartX;
                    double newCol0 = Math.max(MIN_COLUMN_WIDTH, col0StartWidth + delta);

                    double totalAvailable = getWidth() - getInsets().getLeft() - getInsets().getRight()
                        - DIVIDER_WIDTH - getHgap() * 2;
                    double maxCol0 = totalAvailable - MIN_COLUMN_WIDTH;
                    newCol0 = Math.min(newCol0, maxCol0);

                    col0Width.set(newCol0);
                    e.consume();
                });

                add(divider, 1, row);

                // Editor with width bound to remaining space
                Node editor = getEditor(item);
                if (editor instanceof Region) {
                    ((Region) editor).setMinWidth(MIN_COLUMN_WIDTH);
                    ((Region) editor).setMaxWidth(Double.MAX_VALUE);
                }
                label.setLabelFor(editor);
                StackPane editorContainer = new StackPane(editor);
                editorContainer.setPadding(new Insets(3, 0, 3, 0));

                // Bind editor width = total width - col0 - divider - gaps - insets
                editorContainer.prefWidthProperty().bind(
                widthProperty()
                    .subtract(insetsLeftProperty())
                    .subtract(insetsRightProperty())
                    .subtract(col0Width)
                    .subtract(DIVIDER_WIDTH)
                    .subtract(getHgap() * 2)
                );

                add(editorContainer, 2, row);

                row++;
            }
        }

        private DoubleBinding insetsLeftProperty() {
            return Bindings.createDoubleBinding(() -> getInsets().getLeft(), insetsProperty());
        }

        private DoubleBinding insetsRightProperty() {
            return Bindings.createDoubleBinding(() -> getInsets().getRight(), insetsProperty());
        }

        @SuppressWarnings("unchecked")
        private Node getEditor(Item item) {
        @SuppressWarnings("rawtypes")
            PropertyEditor editor = getSkinnable().getPropertyEditorFactory().call(item);
            if (editor == null) {
                editor = new AbstractPropertyEditor<>(item, new TextField(), true) {
                    {
                        getEditor().setEditable(false);
                        getEditor().setDisable(true);
                    }

                    @Override
                    protected ObservableValue<Object> getObservableValue() {
                        return (ObservableValue<Object>) (Object) getEditor().textProperty();
                    }

                    @Override
                    public void setValue(Object value) {
                        getEditor().setText(value == null ? "" : value.toString()); //$NON-NLS-1$
                    }
                };
            } else if (!item.isEditable()) {
                editor.getEditor().setDisable(true);
            }
            editor.setValue(item.getValue());
            return editor.getEditor();
        }
    }

}
