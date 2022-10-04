import React, { useState } from 'react';

import {
  Button,
  Card,
  ControlGroup,
  FormGroup,
  InputGroup,
  Tab,
  Tabs,
  Toaster,
} from '@blueprintjs/core';
import { Container, Row, Col } from 'react-bootstrap';

import { Loading } from 'components/shared/Loading';
import { GrammarContext } from 'contexts/GrammarContext';
import axios from 'axios';
import { CopyToClipboard } from 'react-copy-to-clipboard';
import { useFormik } from 'formik';
import * as Yup from 'yup';
import { TestIndexingAnalysisComponent } from './TestIndexingAnalysis';
import { FileState, TestIndexingCodeEditorComponent } from './TestIndexingCodeEditor';

const toaster = Toaster.create();

type Node = {
  id: string
  name: string
  type: string
  path: string
}

type Edge = {
  from: string
  to: string
  type: string
  path: string
}

function NodeDBComponent(props: { nodes: Node[]}) {
  const [results, setResults] = useState<Node[]>([]);

  const formik = useFormik({
    initialValues: {
      typeQuery: '',
      nameQuery: '',
      idQuery: '',
      pathQuery: ''
    },
    onSubmit: (values, { resetForm }) => {
      const results = props.nodes.filter((n) => {
        return (!values.idQuery || (n.id === values.idQuery)) && 
          (!values.nameQuery || (n.name === values.nameQuery)) &&
          (!values.typeQuery || (n.type === values.typeQuery)) &&
          (!values.pathQuery || (n.path === values.pathQuery))
      })

      setResults(results)
    }
  });

  return <Container>
    <Row>
      <Col>
        {`${results.length} of ${props.nodes.length} nodes`}
        <CopyToClipboard 
          text={JSON.stringify(props.nodes, null, 2)}
          onCopy={() => {
            toaster.show({
              message: "Copied to clipboard",
              timeout: 3000
            });
          }}
        >
          <Button icon="clipboard" />
        </CopyToClipboard>
      </Col>
    </Row>
    <Row style={{paddingTop: 20}}>
      <Col>
        <form onSubmit={formik.handleSubmit}>
            <FormGroup
              label="Queries"
            >
              <InputGroup
                onChange={formik.handleChange} 
                name="pathQuery"
                placeholder="Path Search..."
                value={formik.values.pathQuery}
              />              
              <InputGroup
                onChange={formik.handleChange} 
                name="typeQuery"
                placeholder="Type Search..."
                value={formik.values.typeQuery}
              />
              <InputGroup
                onChange={formik.handleChange} 
                name="idQuery"
                placeholder="Id Search..."
                value={formik.values.idQuery}
              />
              <InputGroup
                onChange={formik.handleChange} 
                name="nameQuery"
                placeholder="Name Search..."
                value={formik.values.nameQuery}
              />
              <ControlGroup>
                <Button icon="search" intent="primary" minimal={true} type="submit" />
                <CopyToClipboard
                      text={JSON.stringify(results, null, 2)}
                      onCopy={() => {
                        toaster.show({
                          message: "Copied to clipboard",
                          timeout: 3000
                        });
                      }}
                    >
                  <Button icon="clipboard" minimal={true}/>
                </CopyToClipboard>
              </ControlGroup>
            </FormGroup>
          </form>
      </Col>
    </Row>
    <Row>
      <Col>
        <pre>{JSON.stringify(results, null, 2)}</pre>
      </Col>
    </Row>
  </Container>
}

function EdgeDBComponent(props: { edges: Edge[]}) {
  const [results, setResults] = useState<Edge[]>([]);

  const formik = useFormik({
    initialValues: {
      typeQuery: '',
      fromQuery: '',
      toQuery: '',
      pathQuery: '',
    },
    onSubmit: (values, { resetForm }) => {
      const results = props.edges.filter((n) => {
        return (!values.fromQuery || (n.from === values.fromQuery)) && 
          (!values.toQuery || (n.to === values.toQuery)) &&
          (!values.typeQuery || (n.type === values.typeQuery)) && 
          (!values.pathQuery || (n.path === values.pathQuery))
      })

      setResults(results)
    }
  });

  return <Container>
    <Row>
      <Col>
        {`${results.length} of ${props.edges.length} edges`}
        <CopyToClipboard 
          text={JSON.stringify(props.edges, null, 2)}
          onCopy={() => {
            toaster.show({
              message: "Copied to clipboard",
              timeout: 3000
            });
          }}
        >
          <Button icon="clipboard" />
        </CopyToClipboard>
      </Col>
    </Row>
    <Row style={{paddingTop: 20}}>
      <Col>
        <form onSubmit={formik.handleSubmit}>
          <FormGroup
            label="Queries"
          >
            <InputGroup
              onChange={formik.handleChange} 
              name="pathQuery"
              placeholder="Path Search..."
              value={formik.values.pathQuery}
            />            
            <InputGroup
              onChange={formik.handleChange} 
              name="typeQuery"
              placeholder="Type Search..."
              value={formik.values.typeQuery}
            />
            <InputGroup
              onChange={formik.handleChange} 
              name="fromQuery"
              placeholder="From Search..."
              value={formik.values.fromQuery}
            />
            <InputGroup
              onChange={formik.handleChange} 
              name="toQuery"
              placeholder="To Search..."
              value={formik.values.toQuery}
            />
            <ControlGroup>
              <Button icon="search" intent="primary" minimal={true} type="submit" />
              <CopyToClipboard
                    text={JSON.stringify(results, null, 2)}
                    onCopy={() => {
                      toaster.show({
                        message: "Copied to clipboard",
                        timeout: 3000
                      });
                    }}
                  >
                <Button icon="clipboard" minimal={true}/>
              </CopyToClipboard>
            </ControlGroup>
          </FormGroup>
        </form>
      </Col>
    </Row>
    <Row><Col>
      <pre>{JSON.stringify(results, null, 2)}</pre>
    </Col></Row>
  </Container>
}

type DataState = {
  nodes: Node[]
  edges: Edge[]
  analysis: {
    file: string
    analysis: string
  }[]
  links: any[]
}

export function TestIndexingContainer() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<any | null>(null)
  const [data, setData] = useState<DataState>({
    nodes: [],
    edges: [],
    analysis: [],
    links: []
  })
  const [language, setLanguage] = useState('javascript')

  const { grammars, loadingGrammars } = React.useContext(GrammarContext)

  const runAnalysis = React.useCallback((payload: FileState[]) => {
    setLoading(true);
    setError(null);

    return axios({
      method: 'POST',
      url: `/api/orgs/-1/test-index/${language}`,
      data: payload,
    }).then((resp: any) => {
      setLoading(false)
      setData(resp.data)
    }).catch((e: any) => {
      console.error(e);
      setLoading(false);
      setError(e.response.data);
      return Promise.reject(e);
    });
  },[language])

  return <>
    <Loading loading={loadingGrammars} />
    <Container>
      <Row style={{paddingTop: 20}}>
        <Col xs={12}>
          <Card>
            <TestIndexingCodeEditorComponent
              performIndex={runAnalysis}
              loading={loading}
              error={error}
              placeholder="Code goes here..."
              //
              languages={Object.keys(grammars || {}) || []}
              selectedLanguage={language}
              setLanguage={setLanguage}
            />
          </Card>
        </Col>
      </Row>
      <Row style={{paddingTop: 20}}>
        <Col xs={12}>
          <Card>
            <Tabs>
              <Tab id="analysis" title="Analysis" panel={          
                <TestIndexingAnalysisComponent analysis={data.analysis}/>            
              }/>              
              <Tab id="nodes" title="Nodes" panel={            
                <NodeDBComponent nodes={data.nodes} />
              }/>
              <Tab id="edges" title="Edges" panel={
                <EdgeDBComponent edges={data.edges} />
              }/>
              <Tab id="links" title="Links" panel={
                <pre>
                  {JSON.stringify(data.links, null, 2)}
                </pre>
              }/>
            </Tabs>
          </Card>
        </Col>
      </Row>      
    </Container>
  </>
}
