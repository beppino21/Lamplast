package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;
import org.eclnt.jsfserver.pagebean.IDestroyable;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSLFA1;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.DefaultBeanListWithEditorListener;
import org.eclnt.dataapp.controller.app.BeanInstanceDataGridControllerDOFW;
import org.eclnt.dataapp.controller.app.IBeanInstanceController;
import org.eclnt.dataapp.controller.app.IBeanDataGridController;
import org.eclnt.dataapp.view.app.datagridmgmt.CCDataGridView2DOFWWrapperWithViewMapping;
import org.eclnt.dataapp.view.app.datagridmgmt.IDataGridWrapper;
import org.eclnt.dataapp.view.app.BeanDataGridWithEditor;

@CCGenClass (expressionBase="#{d.FCSLFA1List}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSLFA1List_GENERATED
    extends PageBean
    implements Serializable, IDestroyable
{
    // ------------------------------------------------------------------------
    // inner classes
    // ------------------------------------------------------------------------

    /**
     * Listener class for the grid-part. Extend this listener for listening
     * to more events of the contained grid. The instance if created in method
     * PageBean.createBeanDataGridWithEditorListener.
     */
    protected class MyBeanDataGridWithEditorListener
        extends DefaultBeanListWithEditorListener<DOFCSLFA1>
    {
        public MyBeanDataGridWithEditorListener() { super(FCSLFA1List_GENERATED.this); }
        @Override
        public void reactOnGridDataRefreshed() { processUpdatedGridData(); }
    }
        
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridWithEditor<DOFCSLFA1> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSLFA1List_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSLFA1List.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSLFA1List}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSLFA1> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditor();
        IBeanDataGridController<DOFCSLFA1> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSLFA1> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSLFA1.class,gridController,listener);
    }    

    public void destroy()
    {
        m_gridWithEditor.destroy();
    }

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    /*
     * This is the implementation if you want the grid to use the entity itself - and not the
     * view for the entity:
     * protected BeanDataGridWithEditor<DOFCSLFA1> createBeanDataGridWithEditor()
     * {
     *     return new BeanDataGridWithEditor<DOFCSLFA1>(DOFCSLFA1.class); 
     * }
     */

    /*
     * This is the implementation in which inside the gird the view instances are managed,
     * but the interface (e.g. when selecting an item) is on entity level.
     */    
    protected BeanDataGridWithEditor<DOFCSLFA1> createBeanDataGridWithEditor()
    {
        return new BeanDataGridWithEditor<DOFCSLFA1>(DOFCSLFA1.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSLFA1> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2DOFWWrapperWithViewMapping<DOFCSLFA1,VIFCSLFA1>(DOFCSLFA1.class,VIFCSLFA1.class);
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSLFA1> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSLFA1>(DOFCSLFA1.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSLFA1> createBeanDataGridWithEditorListener()
    {
        return new MyBeanDataGridWithEditorListener();
    }
    
    /**
     * Called when the data of the grid is loaded or is changed. When extending the grid columns
     * with own column extensions, this might be the right method to fill the additional data
     * content into the grid items.
     */
    protected void processUpdatedGridData() {}
}
