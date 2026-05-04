package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSMARA;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSMARAListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMARAListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSMARA> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSMARAListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMARAListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSMARAListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSMARA> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSMARA> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSMARA> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSMARA.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSMARA> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSMARA>(DOFCSMARA.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSMARA> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSMARA>(DOFCSMARA.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSMARA> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSMARA,VIFCSMARA>(DOFCSMARA.class,VIFCSMARA.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSMARA itemObject)
                    {
                        return FCSMARAListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSMARA itemObject)
                    {
                        return FCSMARAListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSMARA> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSMARA>(DOFCSMARA.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSMARA> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSMARA>(this);
    }
    
    protected String findAvatarIconText(VIFCSMARA itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSMARA itemObject)
    {
        return null;
    }
}
