package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCST001;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCST001ListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCST001ListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCST001> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCST001ListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCST001ListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCST001ListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCST001> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCST001> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCST001> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCST001.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCST001> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCST001>(DOFCST001.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCST001> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCST001>(DOFCST001.class)
        {
            @Override
            protected IDataGridWrapper<DOFCST001> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCST001,VIFCST001>(DOFCST001.class,VIFCST001.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCST001 itemObject)
                    {
                        return FCST001ListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCST001 itemObject)
                    {
                        return FCST001ListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCST001> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCST001>(DOFCST001.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCST001> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCST001>(this);
    }
    
    protected String findAvatarIconText(VIFCST001 itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCST001 itemObject)
    {
        return null;
    }
}
