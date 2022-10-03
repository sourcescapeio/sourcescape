import React from 'react';

import {
  Icon,
  Popover,
  Text,
  Tag,
} from '@blueprintjs/core';

import Container from 'react-bootstrap/Container';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';

import { HighlightedCodeBlockComponent } from 'components/shared/HighlightedCodeBlock';

const NodeTraceResultComponent = (props: { trace: any[], node: any}) => {
  const traces = (props.trace || []).map((t) => (
    <NodeResultComponent terminal={false} {...t} />
  ));
  return <div style={{display: 'flex'}}>
    {traces}
    <NodeResultComponent displayEdge={true} terminal={true} {...props.node}/>
  </div>
};

const GraphTraceResultComponent = (props: any) => {
  return <div style={{display: 'flex'}}>
    <NodeResultComponent displayEdge={false} terminal={true} {...props.terminus.node}/>
  </div>
};

const GenericGraphTraceResultComponent = (props: any) => {
  return <div style={{display: 'flex'}}>
    <GenericNodeResultComponent displayEdge={false} terminal={true} {...props.terminus.node}/>
  </div>
};


const Span = (props: any) => (<span>{props.children}</span>)

const CodeBlock = HighlightedCodeBlockComponent(Span);

const CodeHighlightComponent = (props: any) => {
  console.warn(props, props.nearby)
  return <CodeBlock
    language={"javascript"}
    text={props.nearby.code}
    snippetStartLine={props.nearby.startLine}
    nodes={[{
      start_line: props.range.start.line - props.nearby.startLine,
      start_column: props.range.start.column + 1,
      end_line: props.range.end.line - props.nearby.startLine,
      end_column: props.range.end.column + 1,
      id: props.id,
      type: props.type,
      repo: props.repo,
      file: props.file,
    }]}
    edges={[]}
  />
}

const GenericNodeResultComponent = (props: any) => {
  return <Row>
    {
    <Col>
      <Popover>
        <Tag interactive style={{padding: 15}}>
          <Tag intent="primary">{props.type}</Tag>
          <div style={{width: 300}}>
              {
              props.props.map((p: any) => {
                const [k, v] = p.split("//");

                return <div style={{marginTop: 5}}><b>{k}</b>:<Text ellipsize>{decodeURIComponent(v)}</Text></div>
              })
            }
          </div>
        </Tag>
        <Container>
          <pre>{JSON.stringify(props, null, 2)}</pre>
        </Container>
      </Popover>
    </Col>
    }
  </Row>
}

const NodeResultComponent = (props: any) => {
  const defaultedExtracted = props.extracted || '';

  return <Row>
    {
      ((props.edgeType !== "roota") && props.displayEdge) && <Col style={{display: 'flex'}}>
        <Icon icon="direction-right"/>
        <div>{props.edgeType}</div>
        <Icon icon="direction-right"/>
      </Col>
    }
    <Col>
      <Popover>
        <Tag interactive>
          <p style={{marginBottom: 0, fontSize: 10}}>{props.repo}</p>
          <p style={{marginBottom: 0, fontSize: 10}}>
            {(props.path.length > 50) ? 
              (props.path.substring(0, 50) + "...") :
              props.path
            }
          </p>
          <p><pre>{props.id}</pre></p>          
          <p style={{fontWeight: (props.terminal && 'bold')}}>
            {(defaultedExtracted.length > 50) ? 
              (defaultedExtracted.substring(0, 50) + "...") :
              defaultedExtracted
            }
          </p>
        </Tag>
        <Container>
          <p>{props.id}</p>
          <p><a target="_blank" rel="noopener noreferrer" href={`https://github.com/${props.repo}`}>{props.repo}</a></p>
          <p><a target="_blank" rel="noopener noreferrer" href={`/repos/${props.repo}?file=${props.path}`}>
            {props.path} {props.range.start.line}:{props.range.start.column}-{props.range.end.line}:{props.range.end.column}
          </a></p>
          <Container style={{maxWidth: 600, overflow: 'scroll'}}>
            { props.nearby ? <CodeHighlightComponent {...props} /> : <div>Code size too large</div>}
          </Container>
          <Container style={{maxHeight: 500, maxWidth: 600, overflow: 'scroll'}}>
            <pre>
              {/* {JSON.stringify(props, null, 2)} */}
            </pre>
          </Container>
        </Container>
      </Popover>
    </Col>
  </Row>
};

function CountResultComponent (props: {count: number}) {
  return <Text>{props.count}</Text>
};

function GroupingKeyComponent (props: {key: any}) {
  return <div style={{maxWidth: 300}}>       
    <Text ellipsize={true}>
      {props.key}
    </Text>
  </div>
};

const DefaultComponent = (props: any) => {
  return <pre>{JSON.stringify(props, null, 2)}</pre>
};

type ComponentType = 'node_trace' | 'graph_trace' | 'generic_graph_trace' | 'count' | 'grouping_key';

const COMPONENT_MAP = {
  //
  node_trace: NodeTraceResultComponent,
  graph_trace: GraphTraceResultComponent,
  generic_graph_trace: GenericGraphTraceResultComponent,
  //
  count: CountResultComponent,
  grouping_key: GroupingKeyComponent,
};

export function getComponentForType(componentType: ComponentType) {
  return COMPONENT_MAP[componentType] || DefaultComponent
}
