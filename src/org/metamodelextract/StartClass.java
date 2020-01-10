package org.metamodelextract;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.telosys.tools.dsl.parser.*;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.*;
import org.eclipse.emf.ecore.resource.impl.*;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

public class StartClass {
	/* To store and retrieve ECore Instance Types */
	private static final Map<String, String> typeMap= new HashMap<String, String>();
	
	private static String entitiesDirectory= "";
	
	public static synchronized void putInMap(String key, String value) {
		typeMap.put(key, value);
	}  

	public static synchronized String getFromMap(String key) {
	    return typeMap.get(key);
	}
	
	public static synchronized void updateEntitiesDirectory(String value) {
		entitiesDirectory = value;
	}  

	public static synchronized String getEntitiesDirectory() {
	    return entitiesDirectory;
	}
	
	/* Adding Known list of instance Types */
	public static void updateKnownValues() {
		putInMap("Class","org.eclipse.emf.ecore.impl.EClassImpl");
		putInMap("Enum","org.eclipse.emf.ecore.impl.EEnumImpl");
		putInMap("Attribute","org.eclipse.emf.ecore.impl.EAttributeImpl");
		putInMap("Reference","org.eclipse.emf.ecore.impl.EReferenceImpl");
		putInMap("Generic","org.eclipse.emf.ecore.impl.EGenericTypeImpl");
		putInMap("LongObject","ELongObject");
		putInMap("IntegerObject","EIntegerObject");
		putInMap("Integer","EInt");
		putInMap("String","EString");
		putInMap("EENumImpl","EENumImpl");
		putInMap("EENum","EENum");
		putInMap("EntityString","string");
		putInMap("EntityInteger","int");
		putInMap("EntityLong","long");
	}
	
	public static void main(String[] args) {
		/*
		 * Following Facade pattern to
		 * 1. Extract  Ecore Entities
		 * 2. Create Directories and Files for models and entities
		 * 3. Launch Generation
		 */
		extractData();
	}
	
	public static void extractData() {
		try {
			// Update Known type instances in map
			updateKnownValues();
			// TODO Auto-generated method stub
			ResourceSet set = new ResourceSetImpl();
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ecore",new XMIResourceFactoryImpl());
			Resource packageResource = set.getResource(URI.createURI("file:///c:/Users/mthulasi/eclipse-workspace/emf-modelling-project/model/library.ecore"), true);
			var ePackage = (EPackage) packageResource.getContents().get(0);
			createModelAndEntityFolder(ePackage.getName());
			var classifiers = ePackage.getEClassifiers();
			for(int b = 0; b < classifiers.size(); b++) {
				String classifierName = classifiers.get(b).getClass().getName();
				if(getFromMap("Class").equalsIgnoreCase(classifierName)) {
					processClass((EClass) classifiers.get(b));
				}
				else if(getFromMap("Enum").equalsIgnoreCase(classifierName)) {
					processEnum((EEnum) classifiers.get(b));
				}
			}
		}
		catch(Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	public static void createModelAndEntityFolder(String modelName) {
		File modelDirectory = new File("models");
		File entityFolder = new File(modelDirectory+File.separator+modelName+"_model");
		try {
			if(!modelDirectory.exists()) {
				modelDirectory.mkdirs();
				if(!entityFolder.exists())
					entityFolder.mkdirs();
				String modelData = "version=1.0\r\n" + 
						"name="+modelName+"\r\n" + 
						"description=";
				File modelFileDir = new File(modelDirectory+File.separator+modelName+".model");
				FileOutputStream modelFile = new FileOutputStream(modelFileDir);
				modelFile.write(modelData.getBytes());
				modelFile.flush();
				modelFile.close();
				updateEntitiesDirectory(entityFolder.getAbsolutePath());
			}
		}
		catch(SecurityException e) {
			System.out.println("Security error while creating/accessing a directory/file:"+e.getMessage());
		}
		catch(Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	public static void processClass(EClass eClass) {
		try {
			/*
			 * Convert Ecore Class
			 * to Telosys Entity
			 */
			StringBuilder entity = new StringBuilder(eClass.getName());
			entity.append(" {");
			var att = eClass.getEAllAttributes();
			var ref = eClass.getEAllReferences();
			for(var e : att) {
				boolean attributeCondition = getFromMap("LongObject").equalsIgnoreCase(e.getEType().getName())
					|| getFromMap("String").equalsIgnoreCase(e.getEType().getName())
					|| getFromMap("Integer").equalsIgnoreCase(e.getEType().getName())
					|| getFromMap("EENum").equalsIgnoreCase(e.getEType().eClass().getName())
					|| getFromMap("IntegerObject").equalsIgnoreCase(e.getEType().getName());
			
				if(attributeCondition)
					entity.append("\n"+ processClassFeature(e));
			}
			for(var r: ref) {
				entity.append("\n"+processClassReference(r));
			}
			entity.append("\n}");
			createEntityFile(entity.toString(), eClass.getName());
		}
		catch(Exception e) {
			System.out.println(e.getMessage().toString());
		}
	}
	
	public static void createEntityFile(String entityData, String entityName) {
		try {
			File entityFileDir = new File(getEntitiesDirectory()+File.separator+entityName+".entity");
			FileOutputStream entityFile = new FileOutputStream(entityFileDir);
			entityFile.write(entityData.getBytes());
			entityFile.flush();
			entityFile.close();
		}
		catch(Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	public static String processClassFeature (EAttribute attribute) {
		/*
		 * Adapt ECore Model feature to an Entity Feature
		 * Example: Convert ELong to long
		 * ToDo: Make a neat approach for mapping instead of switch case
		 */
		String name = attribute.getName() + ":";
		String type = "";
		boolean isENum = false;
		switch(attribute.getEType().getName()) {
		case "ELongObject":
			type = "long";
			break;
		case "EIntegerObject":
		case "EInt":
			type = "int";
			break;
		case "EStringObject":
		case "EString":
			type = "string";
			break;
		case "EENum":
			type = "enum";
			break;
		default:
		{
			if(attribute.getEType().eClass().getName() == "EEnum") {
				type = attribute.getEType().getName();
				isENum = true;
			}
		}
		}
		String isID = attribute.isID()? "@Id": "";
		String finalAttr = attribute.isID() && !isENum? 
				name + type + "{" + isID + "};": 
					isENum? name + "#" + type + ";": name + type+";" ;
		// return finalAttr;
		return finalAttr;
		}
	
	public static String processClassReference (EReference reference) {
		/*
		 * Adapt ECore reference relationship
		 * to Telosys Entity Reference Type
		 * Using upperbound and lowerbound values,
		 * figure out the relationship type
		 * one-to-one or one-to-many
		 */
		try {
			String finalRef = "";
			if(reference.getLowerBound() == 0 && reference.getUpperBound() == -1) {
				finalRef = reference.getName() +" : " +reference.getEType().getName()+"[];";
			}
			if(reference.getLowerBound() == 1 && reference.getUpperBound() == 1) {
				finalRef = reference.getName() +" : " +reference.getEType().getName()+";";
			}
			return finalRef;
		}
		catch(Exception e) {
			System.out.println(e.getMessage());
			return null;
		}
	}
	
	public static void processClassGeneric (EGenericType generic) {
		System.out.println(generic.eContents());
	}
	
	public static void processEnum(EEnum eEnum) {
		StringBuilder enumEntity = new StringBuilder(eEnum.getName()+ 
				": string {");
		var enumValues = eEnum.getELiterals();
		for (var value: enumValues) {
			enumEntity.append("\n"+value+",");
		}
		enumEntity.append("\n}");
		System.out.println(enumEntity);
	}
}