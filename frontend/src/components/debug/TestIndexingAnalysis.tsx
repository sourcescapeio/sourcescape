import React, { useState } from 'react';

import { useFormik } from 'formik';
import jmespath from 'jmespath';
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
import { CopyToClipboard } from 'react-copy-to-clipboard';
import { Container, Row, Col } from 'react-bootstrap';

const toaster = Toaster.create();

function SingleAnalysisComponent(props: { file: string, analysis: any} ) {

  const [result, setResult] = useState<any>(props.analysis)
  const [query, setQuery] = useState('')
  
  const formik = useFormik({
    initialValues: {
      query: '',
    },
    onSubmit: async (values, { resetForm }) => {
      console.warn(values)

      if(values.query) {
        setResult(jmespath.search(props.analysis, values.query));
      } else {
        setResult(props.analysis);
      }

      setQuery(values.query);

      // setResults(results)
    }
  });

  // set state if analysis changes
  React.useEffect(() => {
    setResult(props.analysis)
  }, [props.analysis]);

  return <Container>
    <Row style={{paddingTop: 20}}>
      <Col>
        <form onSubmit={formik.handleSubmit}>
          <FormGroup
            label="Queries"
          >
            <InputGroup
              onChange={formik.handleChange} 
              name="query"
              placeholder="JQ query..."
              value={formik.values.query}
            />
            <ControlGroup>
              <Button icon="search" intent="primary" minimal={true} type="submit" />
              <CopyToClipboard
                    text={JSON.stringify(result, null, 2)}
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
        <pre>
          <b>{query}</b>
        </pre>
        <pre>
          {JSON.stringify(result, null, 2)}
        </pre>
      </Col>
    </Row>
  </Container>    
}


export function TestIndexingAnalysisComponent(props: {analysis: { file: string, analysis: string}[]}) {
  return <Tabs defaultSelectedTabId={"analysis_0"}>
    {props.analysis.map((aa, idx) => {
      return <Tab id={`analysis_${idx}`} title={aa.file} panel={  
        <SingleAnalysisComponent
          file={aa.file}
          analysis={JSON.parse(aa.analysis)}
        />
      }/>
    })}
  </Tabs>
}
