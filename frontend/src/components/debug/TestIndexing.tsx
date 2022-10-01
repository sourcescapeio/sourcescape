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

import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';

import { ConsoleQueryComponent } from 'components/console/lib/ConsoleQuery';
import { Loading } from 'components/shared/Loading';
import { Container } from 'react-bootstrap';
import { GrammarContext } from 'contexts/GrammarContext';
import axios from 'axios';
import { CopyToClipboard } from 'react-copy-to-clipboard';
import { useFormik } from 'formik';
import * as Yup from 'yup';


type Node = {
  id: string
  name: string
  type: string
}

type Edge = {
  from: string
  to: string
  type: string
}

function NodeDBComponent(props: { nodes: Node[]}) {
  const [results, setResults] = useState<Node[]>([]);

  const formik = useFormik({
    initialValues: {
      typeQuery: '',
      nameQuery: '',
      idQuery: '',
    },
    onSubmit: (values, { resetForm }) => {
      const results = props.nodes.filter((n) => {
        return (!values.idQuery || (n.id === values.idQuery)) && 
          (!values.nameQuery || (n.name === values.nameQuery)) &&
          (!values.typeQuery || (n.type === values.typeQuery))
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
      <Col xs={2}>    
        <form onSubmit={formik.handleSubmit}>
            <FormGroup
              label="Queries"
            >
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
    },
    onSubmit: (values, { resetForm }) => {
      const results = props.edges.filter((n) => {
        return (!values.fromQuery || (n.from === values.fromQuery)) && 
          (!values.toQuery || (n.to === values.toQuery)) &&
          (!values.typeQuery || (n.type === values.typeQuery))
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
      <Col xs={2}>
        <form onSubmit={formik.handleSubmit}>
          <FormGroup
            label="Queries"
          >
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



const toaster = Toaster.create();

export function TestIndexingContainer() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<any | null>(null)
  const [data, setData] = useState<any | null>({})
  const [language, setLanguage] = useState('javascript')

  const { grammars, loadingGrammars } = React.useContext(GrammarContext)

  const runAnalysis = React.useCallback((payload: string) => {
    setLoading(true);
    setError(null);

    return axios({
      method: 'POST',
      url: `/api/orgs/-1/test-index/${language}`,
      data: {
        text: payload
      },
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
            <ConsoleQueryComponent 
              search={runAnalysis}
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
              <Tab id="nodes" title="Nodes" panel={            
                <NodeDBComponent nodes={data.nodes || []} />
              }/>
              <Tab id="edges" title="Edges" panel={
                <EdgeDBComponent edges={data.edges || []} />
              }/>
              <Tab id="analysis" title="Analysis" panel={          
                <div>
                  <CopyToClipboard 
                    text={data.analysis}
                    onCopy={() => {
                      toaster.show({
                        message: "Copied to clipboard",
                        timeout: 3000
                      });
                    }}
                  >
                    <Button icon="clipboard" />
                  </CopyToClipboard>
                  <pre>
                    {(data.analysis || '').slice(0, 1000)}
                    ...
                  </pre>
                </div>                
              }/>
            </Tabs>
          </Card>
        </Col>
      </Row>      
    </Container>
  </>
}
