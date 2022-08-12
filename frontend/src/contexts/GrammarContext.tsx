import axios from "axios";
import { useEffect, useState, createContext } from "react"

// TODO: need a grammar type

type GrammarState = {
  grammars: {[_: string]: any} | null,
  loadingGrammars: boolean,
}

export const GrammarContext = createContext<GrammarState>({grammars: null, loadingGrammars: true});

export function GrammarProvider(props: { children: React.ReactNode}) {

  const [grammars, setGrammars] = useState<{[_: string]: any} | null>(null);
  const [loadingGrammars, setLoadingGrammars] = useState(true);

  useEffect(() => {
    axios({
      method: 'GET',
      url: '/api/grammars'
    }).catch((e: any) => {
      return Promise.reject(e);
    }).then((resp: any) => {
      const data = resp.data;
      setLoadingGrammars(false);
      setGrammars(data.grammars)
    });
  }, []);
  
  return <GrammarContext.Provider value={{grammars, loadingGrammars}}>
    {props.children}
  </GrammarContext.Provider>
}
