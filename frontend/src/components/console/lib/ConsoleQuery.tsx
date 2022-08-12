import React from 'react';

import { Formik, useFormik } from 'formik';
import * as Yup from 'yup';

import {
  Button,
  TextArea,
  Spinner,
  Callout,
  Popover,
  AnchorButton,
  Menu,
  MenuItem,
} from '@blueprintjs/core';

import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Container from 'react-bootstrap/Container';

export function ConsoleQueryComponent (props: {
  selectedLanguage: string,
  languages: string[],
  setLanguage?: (_: string) => void,
  error: any | null,
  loading: boolean,
  //
  placeholder?: string,
  initialize?: string,
  //
  search: (_: string) => void
}) {
  // const { selectedLanguage, languages, selectLanguage } = this.props;

  const formik = useFormik({
    initialValues: {
      query: props.initialize || ''
    },
    onSubmit: (values, { resetForm }) => {
      props.search(values.query)
    },
    validationSchema: Yup.object().shape({
      query: Yup.string()
        .min(1)
        .required('Required'),
    })
  });

  return (     
    <form onSubmit={formik.handleSubmit}>
      <Container>
        <Row style={{paddingTop: 20}}>
          <Col>
            <TextArea 
              id="query"
              placeholder={props.placeholder}
              style={{width: '100%', minHeight: 150}}
              fill={true}
              onChange={formik.handleChange}
              // onKeyDown={(e) => {
              //   const keyCode = e.keyCode || e.which;
              //   if (keyCode === 9) {
              //     e.preventDefault();
              //     const { selectionStart, selectionEnd } = e.target;
              //     const { query } = formik.values;
              //     setFieldValue('query', 
              //       query.substring(0, selectionStart) + '    ' + query.substring(selectionEnd, query.length)
              //     );
              //   }
              // }}
              value={formik.values.query}
            />
          </Col>
        </Row>
        <Row style={{paddingTop: 20}}>
          <div style={{paddingLeft: 15, display: 'flex'}}>
            <Popover
              position="bottom-left"
              minimal={true}
              autoFocus={false}
              disabled={!props.setLanguage}
            >
              <AnchorButton disabled={!props.setLanguage} minimal={true} icon="code" text={props.selectedLanguage} />
              <Menu>
                {
                  props.languages.map((lang) => {
                    const isLang = lang === props.selectedLanguage;
                    return <MenuItem 
                      text={lang} 
                      disabled={isLang} 
                      onClick={() => props.setLanguage && props.setLanguage(lang)}
                      icon={isLang ? "tick" : undefined}
                    />
                  })
                }
              </Menu>
            </Popover>                
            <Button 
              intent="primary"
              icon="play"
              type="submit"
            >
              Execute
            </Button>
          </div>
          <div style={{paddingLeft: 10, paddingTop: 5}}>
            {props.loading && <Spinner size={20}/>}
          </div>
        </Row>
        { props.error && <Callout intent="danger">
          {JSON.stringify(props.error, null, 2)}
        </Callout> }
      </Container>
    </form>
  )
}
