///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2003 Thomas Morton
//
//This library is free software; you can redistribute it and/or
//modify it under the terms of the GNU Lesser General Public
//License as published by the Free Software Foundation; either
//version 2.1 of the License, or (at your option) any later version.
//
//This library is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Lesser General Public License for more details.
//
//You should have received a copy of the GNU Lesser General Public
//License along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//////////////////////////////////////////////////////////////////////////////
package opennlp.tools.coref;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.Adjective;
import net.didion.jwnl.data.FileDictionaryElementFactory;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.Pointer;
import net.didion.jwnl.data.PointerType;
import net.didion.jwnl.data.Synset;
import net.didion.jwnl.data.VerbFrame;
import net.didion.jwnl.dictionary.FileBackedDictionary;
import net.didion.jwnl.dictionary.MorphologicalProcessor;
import net.didion.jwnl.dictionary.file_manager.FileManager;
import net.didion.jwnl.dictionary.file_manager.FileManagerImpl;
import net.didion.jwnl.dictionary.morph.DefaultMorphologicalProcessor;
import net.didion.jwnl.dictionary.morph.DetachSuffixesOperation;
import net.didion.jwnl.dictionary.morph.LookupExceptionsOperation;
import net.didion.jwnl.dictionary.morph.LookupIndexWordOperation;
import net.didion.jwnl.dictionary.morph.Operation;
import net.didion.jwnl.dictionary.morph.TokenizerOperation;
import net.didion.jwnl.princeton.data.PrincetonWN17FileDictionaryElementFactory;
import net.didion.jwnl.princeton.file.PrincetonRandomAccessDictionaryFile;

/**
 * An implementation of the Dictionary interface using the JWNL library. 
 */
public class JWNLDictionary implements Dictionary {
  
  private net.didion.jwnl.dictionary.Dictionary dict;
  private MorphologicalProcessor morphy;
  private static String[] empty = new String[0];
  
  public JWNLDictionary(String searchDirectory) throws IOException, JWNLException {
    PointerType.initialize();
	Adjective.initialize();
	VerbFrame.initialize();
    Map suffixMap = new HashMap();
    suffixMap.put(POS.NOUN,new String[][] {{"s",""},{"ses","s"},{"xes","x"},{"zes","z"},{"ches","ch"},{"shes","sh"},{"men","man"},{"ies","y"}});
    suffixMap.put(POS.VERB,new String[][] {{"s",""},{"ies","y"},{"es","e"},{"es",""},{"ed","e"},{"ed",""},{"ing","e"},{"ing",""}});
    suffixMap.put(POS.ADJECTIVE,new String[][] {{"er",""},{"est",""},{"er","e"},{"est","e"}});
    DetachSuffixesOperation tokDso = new DetachSuffixesOperation(suffixMap);
    tokDso.addDelegate(DetachSuffixesOperation.OPERATIONS,new Operation[] {new LookupIndexWordOperation(),new LookupExceptionsOperation()});
    TokenizerOperation tokOp = new TokenizerOperation(new String[] {" ","-"});
    tokOp.addDelegate(TokenizerOperation.TOKEN_OPERATIONS,new Operation[] {new LookupIndexWordOperation(),new LookupExceptionsOperation(),tokDso});
    DetachSuffixesOperation morphDso = new DetachSuffixesOperation(suffixMap);
    morphDso.addDelegate(DetachSuffixesOperation.OPERATIONS,new Operation[] {new LookupIndexWordOperation(),new LookupExceptionsOperation()});
    Operation[] operations = {new LookupExceptionsOperation(), morphDso , tokOp};
    morphy = new DefaultMorphologicalProcessor(operations);
    FileManager manager = new FileManagerImpl(searchDirectory,PrincetonRandomAccessDictionaryFile.class);
    FileDictionaryElementFactory factory = new PrincetonWN17FileDictionaryElementFactory();
    FileBackedDictionary.install(manager, morphy,factory,true);
    dict = net.didion.jwnl.dictionary.Dictionary.getInstance();
    morphy = dict.getMorphologicalProcessor();
  }

  public String[] getLemmas(String word, String pos) {
    try {
      List lemmas = morphy.lookupAllBaseForms(POS.NOUN,word);
      return((String[]) lemmas.toArray(new String[lemmas.size()]));
    }
    catch (JWNLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public String getSenseKey(String lemma, String pos,int sense) {
    try {
      IndexWord iw = dict.getIndexWord(POS.NOUN,lemma);
      if (iw == null) {
        return null;
      }
      return String.valueOf(iw.getSynsetOffsets()[sense]);
    }
    catch (JWNLException e) {
      e.printStackTrace();
      return null;
    }
    
  }
  
  public int getNumSenses(String lemma, String pos) {
    try {
      IndexWord iw = dict.getIndexWord(POS.NOUN,lemma);
      if (iw == null){
        return 0;
      }
      return iw.getSenseCount();
    }
    catch (JWNLException e) {
      return 0;
    }
  }
  
  private void getParents(Synset synset, List parents) throws JWNLException {
    Pointer[] pointers = synset.getPointers();
    for (int pi=0,pn=pointers.length;pi<pn;pi++) {
      if (pointers[pi].getType() == PointerType.HYPERNYM) {
        Synset parent = pointers[pi].getTargetSynset();
        parents.add(String.valueOf(parent.getOffset()));
        getParents(parent,parents);
      }
    }
  }

  public String[] getParentSenseKeys(String lemma, String pos, int sense) {
    //System.err.println("JWNLDictionary.getParentSenseKeys: lemma="+lemma);
    try {
      IndexWord iw = dict.getIndexWord(POS.NOUN,lemma);
      if (iw != null) {
        Synset synset = iw.getSense(sense+1);
        List parents = new ArrayList();
        getParents(synset,parents);
        return (String[]) parents.toArray(new String[parents.size()]);
      }
      else {
        return empty;
      }
    }
    catch (JWNLException e) {
      e.printStackTrace();
      return null;
    }
  }
  
  public static void main(String[] args) throws IOException, JWNLException {
    String searchDir = System.getProperty("WNSEARCHDIR");
    System.err.println("searchDir="+searchDir);
    if (searchDir != null) {
      Dictionary dict = new JWNLDictionary(System.getProperty("WNSEARCHDIR"));
      String word = args[0];
      String[] lemmas = dict.getLemmas(word,"NN");
      for (int li=0,ln=lemmas.length;li<ln;li++) {
        for (int si=0,sn=dict.getNumSenses(lemmas[li],"NN");si<sn;si++) {
          System.out.println(lemmas[li]+" ("+si+")\t"+java.util.Arrays.asList(dict.getParentSenseKeys(lemmas[li],"NN",si)));
        }
      }
    }
  }
}
