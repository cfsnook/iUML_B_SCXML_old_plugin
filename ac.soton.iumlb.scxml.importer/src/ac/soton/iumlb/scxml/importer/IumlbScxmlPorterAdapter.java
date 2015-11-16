package ac.soton.iumlb.scxml.importer;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eventb.emf.core.Attribute;
import org.eventb.emf.core.AttributeType;
import org.eventb.emf.core.CoreFactory;
import org.eventb.emf.core.CorePackage;
import org.eventb.emf.core.EventBElement;
import org.eventb.emf.core.EventBNamed;
import org.eventb.emf.core.EventBNamedCommentedComponentElement;
import org.eventb.emf.core.EventBNamedCommentedElement;
import org.eventb.emf.core.EventBNamedCommentedPredicateElement;
import org.eventb.emf.core.context.Context;
import org.eventb.emf.core.machine.Event;
import org.eventb.emf.persistence.AttributeIdentifiers;

import ac.soton.eventb.emf.diagrams.importExport.GenerationDescriptor;
import ac.soton.eventb.emf.diagrams.importExport.IPorterAdapter;



public class IumlbScxmlPorterAdapter implements IPorterAdapter {

	@Override
	public URI getComponentURI(GenerationDescriptor generationDescriptor, EObject rootElement) {
		if (generationDescriptor.remove == false && 
				generationDescriptor.feature == CorePackage.Literals.PROJECT__COMPONENTS &&
				generationDescriptor.value instanceof EventBNamedCommentedComponentElement){
			String projectName = EcoreUtil.getURI(rootElement).segment(1);
			URI projectUri = URI.createPlatformResourceURI(projectName, true);
			String fileName = ((EventBNamed)generationDescriptor.value).getName();
			String ext = generationDescriptor.value instanceof Context? "buc" :  "bum";
			URI fileUri = projectUri.appendSegment(fileName).appendFileExtension(ext); //$NON-NLS-1$
			return fileUri;
		}
		return null;
	}
	
	@Override
	public boolean filter(GenerationDescriptor generationDescriptor) {
		
		//filter any new elements that are already there 	
		if (generationDescriptor.parent==null) return false;
		Object featureValue = generationDescriptor.parent.eGet(generationDescriptor.feature);
		if (featureValue instanceof EList){
			EList<?> list = (EList<?>)featureValue;
			for (Object el : list){
				if (match(el,generationDescriptor.value)) return true;
			}
		}
		
		// filter any new values which are already present by event extension
		if (generationDescriptor.parent instanceof Event){
			for (Object el : getExtendedValues((Event)generationDescriptor.parent,generationDescriptor.feature)){
				if (match(el,generationDescriptor.value)) return true;
			}
		}

		return false;
	}


	/*
	 * transitively get all the elements which are present by event extension
	 */
	@SuppressWarnings("unchecked")
	private List<Object> getExtendedValues(Event event, EStructuralFeature feature) {
		if (!(event.isExtended()) || event.getRefines().isEmpty()){
			return new ArrayList<Object>();
		}else{
			Event refinedEvent = event.getRefines().get(0);
			List<Object> extended = getExtendedValues(refinedEvent,feature);

			Object refinedFeatureValue = refinedEvent.eGet(feature);
			if (refinedFeatureValue instanceof List){
				extended.addAll((List<Object>)refinedEvent.eGet(feature));
			}
			return extended;
		}
	}
///end of filter
	
	///match
	/*
	 * test whether two elements should be considered to be the same in event B terms
	 */
	
	@Override
	public boolean match(Object el1, Object el2) {
		if (el1.getClass()!=el2.getClass()) return false;
		if (el1 instanceof EventBNamedCommentedPredicateElement){	
			return stringEquivalent(
					((EventBNamedCommentedPredicateElement)el1).getPredicate(),
					((EventBNamedCommentedPredicateElement)el2).getPredicate()
					);
		} else if (el1 instanceof EventBNamedCommentedElement){
			String s1 = ((EventBNamedCommentedElement)el1).getName();
			String s2 = ((EventBNamedCommentedElement)el2).getName();
			return (s1 != null && s1.equals(s2));
		} else if(el1 instanceof String && el2 instanceof String) {
			return (el1 != null && el1.equals(el2));
		} else return false;
	}

	private boolean stringEquivalent(String s1, String s2) {
		if (s1==null) return s2==null;
		if (s2==null) return false;
		String s1r = s1.replaceAll("\\s", "");
		String s2r = s2.replaceAll("\\s", "");
		return s1r.equals(s2r);
	}

	////end of match
	
	@Override
	public Object getGeneratorId(EObject eObject){
		return eObject instanceof EventBElement ? 
				((EventBElement)eObject).getAttributes().get(AttributeIdentifiers.GENERATOR_ID_KEY)
			:
				null;
	}

	/*
	 * adds attributes to record:
	 * a) the id of the extension that generated this element
	 * b) the fact that this element is generated
	 * 
	 * @param generatedByID
	 * @param element
	 */
	@Override
	public void setGeneratedBy(String generatedByID, Object object) {
		if (object instanceof EventBElement){
			EventBElement element = (EventBElement)object;
			// set the generated property
			element.setLocalGenerated(true);				
			// add an attribute with this generators ID
			Attribute genID =   CoreFactory.eINSTANCE.createAttribute();
			genID.setValue(generatedByID);
			genID.setType(AttributeType.STRING);
			element.getAttributes().put(AttributeIdentifiers.GENERATOR_ID_KEY,genID);
		}
	}
	
	/*
	 * adds attributes to record:
	 * a) the priority of this element for ordering
	 * 
	 * @param priority
	 * @param element
	 */
	@Override
	public void setPriority(int priority, Object object) {
		if (object instanceof EventBElement){
			EventBElement element = (EventBElement)object;
			// add an attribute with the priority for ordering
			Attribute pri =   CoreFactory.eINSTANCE.createAttribute();
			pri.setValue(priority);
			pri.setType(AttributeType.INTEGER);
			element.getAttributes().put(AttributeIdentifiers.PRIORITY_KEY,pri);
		}
	}
	

	
	
	
	/*
	 *The following deals with maintaining the order of generated elements to match the order of
	 *the source element within its containment e.g. statemachine within extensions.
	 *probably not relevant for import/export?
	 *
	
		private Map<String, Integer> extensionOrder = new HashMap<String,Integer>();
		
		// from early on in generate...
		//set up map of extensions ids and their positions
			EventBObject component = sourceElement.getContaining(CorePackage.Literals.EVENT_BNAMED_COMMENTED_COMPONENT_ELEMENT);
			int i=0;
			for (EObject ae : component.getAllContained(CorePackage.Literals.ABSTRACT_EXTENSION, true)){
				if (ae !=null) {
					String id = ((AbstractExtension)ae).getExtensionId();
					extensionOrder.put(id, i++);
				}
			}
			
			
			//from place generated...
								//add the new value to the list at the correct index - i.e. after any higher priority elements and
								//after stuff generated by earlier extensions which has the same priority
								int pos = 0;
								for (int i=0; i<((EList)featureValue).size(); i++){
									Object v = ((EList)featureValue).get(i);
									if(v instanceof EventBElement){
										Attribute at = ((EventBElement)v).getAttributes().get(AttributeIdentifiers.GENERATOR_ID_KEY);
										String gb = (String) (at==null? null : at.getValue());
										Integer od = extensionOrder.get(gb);
										if (od==null) od = extensionOrder.size(); // not an extension => user entered stuff comes last
										at = ((EventBElement)v).getAttributes().get(AttributeIdentifiers.PRIORITY_KEY);
										Integer pr = (Integer) (at==null? null : at.getValue());
										if (pr==null) pr = 0; // no priority => user stuff at priority 0
										//priority order = highest 1..10,0,-1..-10
										Integer exo = extensionOrder.containsKey(generatedByID)? extensionOrder.get(generatedByID) : 0;
										if ((pr>0 && pr < pri) || (pr < 1 && pr > pri) || (pr==pri && od<=exo)){
											pos = i+1;
										};
										
									}
								}

								((EList)featureValue).add(pos, generationDescriptor.value);
			
 * 
 * 
 * 
		*/
}
