
import React, { useCallback, useState } from 'react';

import {
  Card,
} from '@blueprintjs/core';

import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';

import { ConsoleQueryComponent } from './lib/ConsoleQuery';
import { GraphResultsComponent } from './lib/GraphResults';
import { StreamHandler } from 'lib/StreamHandler';

import { Loading } from 'components/shared/Loading';
import { GrammarContext } from 'contexts/GrammarContext';


export function GraphConsoleContainer() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<any | null>(null)
  const [language, setLanguage] = useState('javascript')

  const { grammars, loadingGrammars } = React.useContext(GrammarContext)
  const [size, setSize] = useState(50);  

  const incSize = useCallback(() => {
    setSize(size => (size + 50))
  }, []);

  /**
   * Search info
   */
   const [columns, setColumns] = useState([]);  
   const [data, setData] = useState<any[]>([]);

  const graphSearch = useCallback((q: string) => {
    setLoading(true);
    setError(null);
    setData([]);
    setColumns([]);

    return fetch(`/api/orgs/-1/query/graph/experimental/${language}`, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json;charset=UTF-8',        
      },
      body: JSON.stringify({
        q
      }),
    }).catch((e) => {
      console.error(e);
      e.response.json().then((json: any) => {
        setError(json);
        setLoading(false);
        setData([]);
      })
      return Promise.reject(e);
    }).then((resp: any) => {
      const columnHeader = resp.headers.get('Rambutan-Result') || "{}";
      const columns = JSON.parse(columnHeader).columns;

      setColumns(columns);

      StreamHandler(resp.body, {
        onClose: () => {
          setLoading(false);
        },
        onError: (error) => {
          setError(error);
        },        
        onAppend: (append) => {
          setData(data => [...data, ...append])
        },
      });
    });
  }, [])

  return <>
    <Loading loading={loadingGrammars} />
    {
      // Query textbox
      // Draft js in future
    }
    <Row style={{paddingTop: 20}}>
      <Col xs={12}>
        <Card>
          <ConsoleQueryComponent 
            // initialize={this.props.initialize}
            search={graphSearch}
            loading={loading}
            error={error}
            placeholder="root[type=const,name=...]"
            //
            selectedLanguage={language}
            setLanguage={setLanguage}
            languages={Object.keys(grammars || {})}
          />
        </Card>
      </Col>
    </Row>
    {
      // results
    }
    <Row style={{paddingTop: 20}}>
      <Col xs={12}>
        <Card>
          <GraphResultsComponent
            data={data}
            columns={columns}
            size={size}
            getMore={incSize}
          />
        </Card>
      </Col>
    </Row>
  </>
}
