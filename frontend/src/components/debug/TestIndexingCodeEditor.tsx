import React from 'react';

import {
  Button,
  TextArea,
  Spinner,
  Callout,
  Popover,
  AnchorButton,
  Menu,
  MenuItem,
  Tabs,
  Tab,
  ControlGroup,
} from '@blueprintjs/core';

import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Container from 'react-bootstrap/Container';

export type FileState = {
  file: string
  content: string
}

export function TestIndexingCodeEditorComponent (props: {
  selectedLanguage: string,
  languages: string[],
  setLanguage?: (_: string) => void,
  error: any | null,
  loading: boolean,
  //
  placeholder?: string,
  initialize?: string,
  //
  performIndex: (_: FileState[]) => void,
}) {

  const [fileState, setFileState] = React.useState<FileState[]>([{
    file: 'test.ts',
    content: 'function(){}'
  }])

  const addItem = React.useCallback(() => {
    setFileState(
      [
        ...fileState,
        {
          file: 'test.ts',
          content: 'function(){}'
        }
      ]
    )
  }, [fileState, setFileState]);

  const removeItem = React.useCallback((id: number) => {
    setFileState(
      [
        ...(fileState.slice(0, id)),
        ...(fileState.slice(id + 1, fileState.length)),
      ]
    );
  }, [fileState, setFileState])

  const updateFile = React.useCallback((id: number, content: string) => {
    setFileState(      [
      ...(fileState.slice(0, id)),
      {
        ...fileState[id],
        content,
      },
      ...(fileState.slice(id + 1, fileState.length)),
    ])
  }, [fileState, setFileState])

  const renameFile = React.useCallback((id: number, file: string) => {
    setFileState(      [
      ...(fileState.slice(0, id)),
      {
        ...fileState[id],
        file,
      },
      ...(fileState.slice(id + 1, fileState.length)),
    ])
  }, [fileState, setFileState])  

  return (     
    <Container>
      <Row style={{paddingTop: 20}}>
        <Col>
          <Tabs defaultSelectedTabId="analysis_0">
            { fileState.map((fs, idx) => {
              return <Tab
                id={`analysis_${idx}`}
                title={
                  <ControlGroup>
                    {fs.file}
                    <Button 
                      icon="cross"
                      minimal={true}
                      onClick={() => removeItem(idx)}
                    />
                    <Button 
                      icon="highlight"
                      minimal={true}
                      onClick={() => {
                        const rename = prompt('Rename to?', fs.file);
                        if (rename) {
                          renameFile(idx, rename)
                        }
                      }}
                    />                    
                  </ControlGroup>
                }
                panel={
                  <TextArea 
                    placeholder={props.placeholder}
                    style={{width: '100%', minHeight: 300}}
                    fill={true}
                    onChange={(e) => (updateFile(idx, e.target.value))}
                    value={fs.content}
                  />
                }
              />
            })}
            <Button 
              intent="primary"
              icon="add"
              minimal={true}
              onClick={addItem}
            >
            </Button>
          </Tabs>
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
          <ControlGroup>
            <Button 
              intent="primary"
              icon="download"
              onClick={() => props.performIndex(fileState)}
            >
              Index
            </Button>                 
          </ControlGroup>
        </div>
        <div style={{paddingLeft: 10, paddingTop: 5}}>
          {props.loading && <Spinner size={20}/>}
        </div>
      </Row>
      { props.error && <Callout intent="danger">
        {JSON.stringify(props.error, null, 2)}
      </Callout> }
    </Container>
  )
}
