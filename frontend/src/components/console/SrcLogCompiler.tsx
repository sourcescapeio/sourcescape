import React, { useEffect, useState } from 'react';

import { connect } from 'react-redux';

import {
  Card,
  Tab,
  Tabs,
} from '@blueprintjs/core';

import { Row, Col } from 'react-bootstrap';

import { ConsoleQueryComponent } from 'components/console/lib/ConsoleQuery';
import { Loading } from 'components/shared/Loading';

import axios from 'axios';
import { GrammarContext } from 'contexts/GrammarContext';

function SrcLogResults(props: {data: any}) {
  const keys = Object.keys(props.data);

  const inner = keys.map((k) => {
    const v = props.data[k];

    const vInner = Object.keys(v).filter((k) => {
      return k !== "query";
    }).map((vk) => {
      return <Tab 
        key={vk}
        id={vk}
        title={vk}
        panel={
          <pre>{JSON.stringify(v[vk], null, 2)}</pre>
        }
      />
    });

    return <Tab 
      key={k}
      id={k}
      title={`Chunk ${k}`}
      panel={<Tabs vertical={true}>
        <Tab 
          key="query"
          id="query"
          title="query"
          panel={
            <pre>{v}</pre>
          }
        />
        <Tab 
          key="edges"
          id="edges"
          title="edges"
          panel={
            <pre>{JSON.stringify(v.edges, null, 2)}</pre>
          }
        />
        <Tab 
          key="roots"
          id="roots"
          title="roots"
          panel={
            <pre>{JSON.stringify(v.roots, null, 2)}</pre>
          }
        />          
      </Tabs>}
    />
  });

  return <Card>
    {keys.length > 0 && <Tabs>
      {inner}
    </Tabs>}
  </Card>
}

export function SrcLogCompilerContainer () {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<any | null>(null)
  const [language, setLanguage] = useState('javascript')

  const { grammars, loadingGrammars } = React.useContext(GrammarContext)

  const [data, setData] = useState({})

  const srcLogCompile = (q: string) => {
    setLoading(true);
    setError(null);

    return axios({
      method: 'POST',
      url: `/api/orgs/-1/parse/srclog/${language}`,
      data: {
        q
      },
    }).then((resp) => {
      setLoading(false)
      setData(resp.data)
    }).catch((e) => {
      setError(e.response.data)
      setLoading(false)
    });
  }

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
            search={srcLogCompile}
            loading={loading}
            error={error}
            placeholder="node(A)."
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
        {
          <SrcLogResults data={data}/>
        }
      </Col>
    </Row>
  </>
}
