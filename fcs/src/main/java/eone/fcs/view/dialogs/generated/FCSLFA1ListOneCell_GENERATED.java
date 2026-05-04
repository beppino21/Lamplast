package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSLFA1;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSLFA1ListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSLFA1ListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSLFA1> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSLFA1ListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSLFA1ListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSLFA1ListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSLFA1> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSLFA1> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSLFA1> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSLFA1.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSLFA1> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSLFA1>(DOFCSLFA1.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSLFA1> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSLFA1>(DOFCSLFA1.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSLFA1> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSLFA1,VIFCSLFA1>(DOFCSLFA1.class,VIFCSLFA1.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSLFA1 itemObject)
                    {
                        return FCSLFA1ListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSLFA1 itemObject)
                    {
                        return FCSLFA1ListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSLFA1> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSLFA1>(DOFCSLFA1.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSLFA1> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSLFA1>(this);
    }
    
    protected String findAvatarIconText(VIFCSLFA1 itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSLFA1 itemObject)
    {
        return null;
    }
}
