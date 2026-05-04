package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;
import org.eclnt.jsfserver.pagebean.IDestroyable;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSMOVSAP;
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

@CCGenClass (expressionBase="#{d.FCSMOVSAPList}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPList_GENERATED
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
        extends DefaultBeanListWithEditorListener<DOFCSMOVSAP>
    {
        public MyBeanDataGridWithEditorListener() { super(FCSMOVSAPList_GENERATED.this); }
        @Override
        public void reactOnGridDataRefreshed() { processUpdatedGridData(); }
    }
        
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridWithEditor<DOFCSMOVSAP> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSMOVSAPList_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMOVSAPList.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSMOVSAPList}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSMOVSAP> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditor();
        IBeanDataGridController<DOFCSMOVSAP> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSMOVSAP> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSMOVSAP.class,gridController,listener);
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
     * protected BeanDataGridWithEditor<DOFCSMOVSAP> createBeanDataGridWithEditor()
     * {
     *     return new BeanDataGridWithEditor<DOFCSMOVSAP>(DOFCSMOVSAP.class); 
     * }
     */

    /*
     * This is the implementation in which inside the gird the view instances are managed,
     * but the interface (e.g. when selecting an item) is on entity level.
     */    
    protected BeanDataGridWithEditor<DOFCSMOVSAP> createBeanDataGridWithEditor()
    {
        return new BeanDataGridWithEditor<DOFCSMOVSAP>(DOFCSMOVSAP.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSMOVSAP> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2DOFWWrapperWithViewMapping<DOFCSMOVSAP,VIFCSMOVSAP>(DOFCSMOVSAP.class,VIFCSMOVSAP.class);
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSMOVSAP> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSMOVSAP>(DOFCSMOVSAP.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSMOVSAP> createBeanDataGridWithEditorListener()
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
