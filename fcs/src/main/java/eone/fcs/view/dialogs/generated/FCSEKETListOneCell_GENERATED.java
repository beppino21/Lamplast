package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSEKET;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSEKETListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSEKETListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSEKET> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSEKETListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSEKETListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSEKETListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSEKET> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSEKET> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSEKET> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSEKET.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSEKET> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSEKET>(DOFCSEKET.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSEKET> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSEKET>(DOFCSEKET.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSEKET> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSEKET,VIFCSEKET>(DOFCSEKET.class,VIFCSEKET.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSEKET itemObject)
                    {
                        return FCSEKETListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSEKET itemObject)
                    {
                        return FCSEKETListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSEKET> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSEKET>(DOFCSEKET.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSEKET> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSEKET>(this);
    }
    
    protected String findAvatarIconText(VIFCSEKET itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSEKET itemObject)
    {
        return null;
    }
}
