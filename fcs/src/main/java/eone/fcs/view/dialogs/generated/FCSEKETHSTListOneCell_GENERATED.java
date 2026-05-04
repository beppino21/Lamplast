package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSEKETHST;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSEKETHSTListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSEKETHSTListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSEKETHST> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSEKETHSTListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSEKETHSTListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSEKETHSTListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSEKETHST> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSEKETHST> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSEKETHST> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSEKETHST.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSEKETHST> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSEKETHST>(DOFCSEKETHST.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSEKETHST> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSEKETHST>(DOFCSEKETHST.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSEKETHST> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSEKETHST,VIFCSEKETHST>(DOFCSEKETHST.class,VIFCSEKETHST.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSEKETHST itemObject)
                    {
                        return FCSEKETHSTListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSEKETHST itemObject)
                    {
                        return FCSEKETHSTListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSEKETHST> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSEKETHST>(DOFCSEKETHST.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSEKETHST> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSEKETHST>(this);
    }
    
    protected String findAvatarIconText(VIFCSEKETHST itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSEKETHST itemObject)
    {
        return null;
    }
}
