import React, { useCallback, useEffect, useState } from 'react';

import { connect } from 'react-redux';

import {
  Card,
  Tabs,
  Tab,
  ProgressBar,
} from '@blueprintjs/core';

import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Container from 'react-bootstrap/Container';

import { Loading } from 'components/shared/Loading';

import { ConsoleQueryComponent } from './lib/ConsoleQuery';
import { GraphResultsComponent } from './lib/GraphResults';
import { GraphExplainComponent } from './lib/GraphExplain';
import { StreamHandler } from 'lib/StreamHandler';

import axios from 'axios';
import { GrammarContext } from 'contexts/GrammarContext';
import { refractor } from 'refractor';

export function RelationalConsoleContainer() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<any | null>(null)
  const [language, setLanguage] = useState('javascript')

  const { grammars, loadingGrammars } = React.useContext(GrammarContext)

  /**
   * Search info
   */
  const [columns, setColumns] = useState([]);
  const [explainColumns, setExplainColumns] = useState<any>({});
  const [progress, setProgress] = useState<number | null>(null)
  const [explain, setExplain] = useState<any[]>([]);
  const [isDiff, setIsDiff] = useState(false);
  const [data, setData] = useState<any[]>([])
  const [diffData, setDiffData] = useState<any>({})
  const [returnTime, setReturnTime] = useState<number | null>(null)
  const [finishTime, setFinishTime] = useState<number | null>(null)
  const [sizeEstimate, setSizeEstimate] = useState<number | null>(null)
  const [size, setSize] = useState(50);

  const incSize = useCallback(() => {
    setSize(size => (size + 50))
  }, []);

  const relationalTime = useCallback((q: string) => {
    const queryStartTime = new Date().getTime();

    return fetch(`/api/orgs/-1/query/relational/time/${language}`, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json;charset=UTF-8',        
      },
      body: JSON.stringify({
        q
      }),
    }).then(async (resp) => {
      const json = await resp.json()
      const queryReturnTime = new Date().getTime();
      const returnTime = (queryReturnTime - queryStartTime);
      setReturnTime(returnTime)

      alert(json.trace)
    });
  }, [language])

  const relationalSearch = useCallback((q: string) => {
    setLoading(true)
    setError(null)

    setColumns([])
    setExplainColumns({})
    setExplain([])
    setDiffData({})
    setData([])
    setIsDiff(false)
    setProgress(null)
    setSizeEstimate(null)

    const queryStartTime = new Date().getTime();

    return fetch(`/api/orgs/-1/query/relational/xp/${language}`, {
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
        setError(json)
        setLoading(false)
        setData([])
        setDiffData({})
        setExplain([])
      })
      return Promise.reject(e);
    }).then((resp) => {
      const queryHeaderStr = resp.headers.get('Rambutan-Result') || "{}";
      const queryHeaders = JSON.parse(queryHeaderStr);
      const { columns, sizeEstimate, isDiff } = queryHeaders.results;
      const explainColumns = queryHeaders.explain;

      const queryReturnTime = new Date().getTime();
      const returnTime = (queryReturnTime - queryStartTime);
      
      // setExplainColumns
      setIsDiff(isDiff)
      setReturnTime(returnTime)
      setColumns(columns)
      setExplainColumns(explainColumns)
      setSizeEstimate(sizeEstimate)

      if(!resp.body) {
        return
      }

      StreamHandler(resp.body, {
        onClose: () => {
          const queryEndTime = new Date().getTime();

          const finishTime = (queryEndTime - queryStartTime);
          setFinishTime(finishTime);
          setLoading(false);
        },
        onError: (error) => {
          setError(error);
        },
        onAppend: (append) => {
          const dataAppend = append.filter((a) => (a.type === "data")).map((a) => (a.obj));
          const explainAppend = append.filter((a) => (a.type === "explain")).map((a) => (a.obj));
          const progressAppend = append.filter((a) => (a.type === "progress")).map((a) => a.progress);

          if (isDiff) {
            const diffAppend = dataAppend.reduce((acc, a) => {
              acc[a._diffKey] = a;
              return acc;
            }, {});
            setDiffData((diffData: any) => ({...diffData, ...diffAppend}))
          } else {
            setData(prevData => {
              return  [...prevData, ...dataAppend]
            });
          }          

          setExplain(explain => [...explain, ...explainAppend]);
          setProgress(progress => progressAppend.length ? Math.max(...progressAppend) : progress);
        },
      });
    });
  }, [language]);

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
            search={relationalSearch}
            time={relationalTime}
            loading={loading}
            error={error}
            placeholder="SELECT file:(package.json) ... "
            //
            selectedLanguage={language}
            languages={[
              ...(Object.keys(grammars || {})),
              'generic',
            ]}
            setLanguage={setLanguage}
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
          {(loading && progress && sizeEstimate) && <ProgressBar value={progress / sizeEstimate} />}
          <Tabs>
            <Tab id="results" title="Results" panel={
              <GraphResultsComponent
                // loading={loading}
                // error={error}
                //
                isDiff={isDiff}
                data={data}
                diffData={diffData}
                //
                columns={columns}
                size={size}
                getMore={incSize}
                returnTime={returnTime}
                finishTime={finishTime}
              />
            }/>
            <Tab id="explain" title="Explain" panel={
              <Container>
                <Row>
                  <Col style={{overflowX: 'scroll'}}>
                    { explainColumns && <GraphExplainComponent
                      // nodes={explainColumns.nodes}
                      // edges={explainColumns.edges}
                      columns={explainColumns.columns}
                      data={explain}
                    /> }
                  </Col>
                </Row>
              </Container>
            }/>
          </Tabs>
        </Card>
      </Col>
    </Row>
  </>
}
